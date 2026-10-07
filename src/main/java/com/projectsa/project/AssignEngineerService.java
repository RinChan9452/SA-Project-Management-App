package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC04 Assign Engineer to Project — only while the project is WAITING_FOR_ASSIGN_ENGINEER. The first time any PM
 * may do it and becomes the project's PM in charge; after an approved requirement only that PM in charge may
 * re-confirm, and stays in charge (CLAUDE.md §8).
 */
@Service
public class AssignEngineerService {

    private final ProjectRepository projects;
    private final ProjectEngineerRepository projectEngineers;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public AssignEngineerService(ProjectRepository projects, ProjectEngineerRepository projectEngineers,
                                 EmployeeRepository employees, NotificationService notifications) {
        this.projects = projects;
        this.projectEngineers = projectEngineers;
        this.employees = employees;
        this.notifications = notifications;
    }

    /**
     * Everything the assign page shows.
     *
     * @param engineers       Tech Engineers that can still be added: those having the filter skill, plus the ones
     *                        already ticked (so a tick is never lost when the filter changes)
     * @param alreadyAssigned engineers kept from an earlier round (project came back after an approved requirement)
     * @param skills          filter choices
     * @param anyEngineer     false when no Tech Engineer is registered at all (UC04 precondition)
     */
    public record AssignPage(Project project, List<Employee> engineers, List<ProjectEngineer> alreadyAssigned,
                             List<String> skills, boolean anyEngineer) {
    }

    /** UC04 steps 1–3: the project, the engineer list with skills, and the skill filter. */
    @Transactional(readOnly = true)
    public AssignPage page(Long projectId, CurrentUser user, String skill, Collection<Long> selected) {
        Project project = projectForAssignment(projectId, user);
        List<ProjectEngineer> assigned = projectEngineers.findForProject(projectId);
        Set<Long> assignedIds = assigned.stream().map(pe -> pe.getId().getEmployeeId()).collect(Collectors.toSet());
        Set<Long> selectedIds = idsWithoutNulls(selected);

        List<Employee> allEngineers = employees.findTechEngineersWithSkills();
        List<Employee> shown = allEngineers.stream()
                .filter(e -> !assignedIds.contains(e.getId()))
                .filter(e -> hasSkill(e, skill) || selectedIds.contains(e.getId()))
                .toList();
        return new AssignPage(project, shown, assigned, employees.findSkillNames(), !allEngineers.isEmpty());
    }

    /**
     * UC04 steps 4–5: saves the new engineers, makes the current PM the PM in charge (first round only), moves the project to
     * WORKING and notifies each newly assigned engineer. Engineers already on the project are skipped, so
     * nobody is assigned twice. Selecting nobody is allowed only when the project already has engineers
     * (re-confirming after an approved requirement).
     *
     * @return the newly assigned engineers
     * @throws FieldValidationException  on {@code engineerIds} if nobody is selected or someone is not a Tech Engineer
     * @throws ActionNotAllowedException if the project is not waiting for engineers (or was just changed by someone else)
     * @throws AccessDeniedException     if the project already has another PM in charge
     */
    @Transactional
    public List<Employee> assign(Long projectId, Collection<Long> engineerIds, CurrentUser user) {
        Project project = projectForAssignment(projectId, user);
        Set<Long> alreadyAssigned = projectEngineers.findEngineerIds(projectId);
        Set<Long> newIds = idsWithoutNulls(engineerIds);
        newIds.removeAll(alreadyAssigned);

        List<Employee> engineers = employees.findAllById(newIds);
        if (engineers.size() != newIds.size() || engineers.stream().anyMatch(e -> e.getRole() != Role.TECH)) {
            throw new FieldValidationException("engineerIds", "Only Tech Engineers can be assigned");
        }
        if (alreadyAssigned.isEmpty() && engineers.isEmpty()) {
            throw new FieldValidationException("engineerIds", "Please select at least one engineer");
        }

        Employee pm = employees.getReferenceById(user.getId());
        engineers.forEach(engineer -> projectEngineers.save(new ProjectEngineer(project, engineer, pm)));
        if (project.getPm() == null) {
            project.setPm(pm);
        }
        project.changeStatus(ProjectStatus.WORKING);
        try {
            projects.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Another PM assigned engineers to this project at the same moment
            throw new ActionNotAllowedException("This project was just changed by someone else. Please open it again.");
        }

        String message = "You were assigned to project \"" + project.getProjectName() + "\" (ID " + project.getId()
                + ") by " + user.getName() + ".";
        engineers.forEach(engineer -> notifications.notify(engineer, project, NotificationType.ASSIGNED, message));
        return engineers;
    }

    private Project projectForAssignment(Long projectId, CurrentUser user) {
        ProjectService.requireRole(user, Role.PM);
        Project project = projects.findDetailById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (project.getStatus() != ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER) {
            throw new ActionNotAllowedException("Engineers can only be assigned while the project is \""
                    + ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER.getLabel() + "\". This project is \""
                    + project.getStatus().getLabel() + "\".");
        }
        if (!isFreeOrMine(project, user)) {
            throw new AccessDeniedException("Only the PM in charge of this project can re-confirm its engineers");
        }
        return project;
    }

    /** Whether the user may do UC04 on this project now (used to show the Assign Engineers buttons). */
    public static boolean canAssign(Project project, CurrentUser user) {
        return user.getRole() == Role.PM
                && project.getStatus() == ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER
                && isFreeOrMine(project, user);
    }

    /** No PM in charge yet (first round), or the user is the PM in charge (re-confirm after an approved requirement). */
    private static boolean isFreeOrMine(Project project, CurrentUser user) {
        return project.getPm() == null || project.getPm().getId().equals(user.getId());
    }

    /** Case-insensitive, like §5b query 5.1. A blank filter matches everyone. */
    static boolean hasSkill(Employee engineer, String skill) {
        if (skill == null || skill.isBlank()) {
            return true;
        }
        String wanted = skill.trim();
        return engineer.getSkills().stream().anyMatch(s -> s.getSkill().equalsIgnoreCase(wanted));
    }

    private static Set<Long> idsWithoutNulls(Collection<Long> ids) {
        return ids == null ? new LinkedHashSet<>()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
