package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complete Project (handover, TESTING → FINISH) — only the project's PM in charge, and only when no new requirement
 * came in during the week after the customer test (§8): the project is still TESTING one week after the latest test.
 */
@Service
public class CompleteProjectService {

    /** How long after the customer test the customer may still ask for a new requirement (§8). */
    public static final int REQUIREMENT_WINDOW_DAYS = 7;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final ProjectRepository projects;
    private final ProjectEngineerRepository projectEngineers;
    private final TestScheduleRepository schedules;
    private final NotificationService notifications;

    public CompleteProjectService(ProjectRepository projects, ProjectEngineerRepository projectEngineers,
                                  TestScheduleRepository schedules, NotificationService notifications) {
        this.projects = projects;
        this.projectEngineers = projectEngineers;
        this.schedules = schedules;
        this.notifications = notifications;
    }

    /**
     * Moves the project to FINISH and notifies the Sale in charge, the Presale in charge and the assigned engineers.
     *
     * @throws AccessDeniedException     if the user is not the project's PM in charge
     * @throws ActionNotAllowedException if the project is not TESTING, the week after the test is not over yet,
     *                                   or someone else changed the project at the same moment
     */
    @Transactional
    public Project complete(Long projectId, CurrentUser user) {
        Project project = projects.findDetailById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        checkAllowed(project, user);

        project.changeStatus(ProjectStatus.FINISH);
        try {
            projects.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ActionNotAllowedException("This project was just changed by someone else. Please open it again.");
        }

        String message = "Project \"" + project.getProjectName() + "\" (ID " + project.getId() + ") was completed by "
                + user.getName() + " and is now Finished.";
        for (Employee recipient : recipients(project)) {
            notifications.notify(recipient, project, NotificationType.PROJECT_COMPLETED, message);
        }
        return project;
    }

    /** When Complete Project becomes available: one week after the customer test, or null if no test was set. */
    public static LocalDateTime completeFrom(LocalDateTime latestTestAt) {
        return latestTestAt == null ? null : latestTestAt.plusDays(REQUIREMENT_WINDOW_DAYS);
    }

    /** True when the user may complete this project now (for the button on UC03 and the PM dashboard). */
    public static boolean canComplete(Project project, LocalDateTime latestTestAt, CurrentUser user) {
        return isPmInCharge(project, user)
                && project.getStatus() == ProjectStatus.TESTING
                && isWindowOver(latestTestAt, LocalDateTime.now());
    }

    /** The week for new requirements is over once its end is reached; no test date means it has not started. */
    public static boolean isWindowOver(LocalDateTime latestTestAt, LocalDateTime now) {
        return latestTestAt != null && !completeFrom(latestTestAt).isAfter(now);
    }

    private static boolean isPmInCharge(Project project, CurrentUser user) {
        return user.getRole() == Role.PM && project.getPm() != null && project.getPm().getId().equals(user.getId());
    }

    private void checkAllowed(Project project, CurrentUser user) {
        if (user == null || user.getRole() != Role.PM) {
            throw new AccessDeniedException("Only a Project Manager can complete a project");
        }
        if (!isPmInCharge(project, user)) {
            throw new AccessDeniedException("Only the PM in charge of this project can complete it");
        }
        if (project.getStatus() != ProjectStatus.TESTING) {
            throw new ActionNotAllowedException("A project can only be completed while it is \""
                    + ProjectStatus.TESTING.getLabel() + "\". This project is \"" + project.getStatus().getLabel() + "\".");
        }
        LocalDateTime testAt = schedules.findLatestTestAt(project.getId());
        if (!isWindowOver(testAt, LocalDateTime.now())) {
            throw new ActionNotAllowedException(testAt == null
                    ? "A project can only be completed after its customer test."
                    : "The project can be completed from " + completeFrom(testAt).format(WHEN) + ", one week after the"
                      + " customer test, if the customer has no new requirement by then.");
        }
    }

    /** Sale in charge, Presale in charge and assigned engineers, each once. */
    private List<Employee> recipients(Project project) {
        Map<Long, Employee> byId = new LinkedHashMap<>();
        byId.put(project.getSale().getId(), project.getSale());
        byId.putIfAbsent(project.getPresale().getId(), project.getPresale());
        for (ProjectEngineer pe : projectEngineers.findForProject(project.getId())) {
            byId.putIfAbsent(pe.getEmployee().getId(), pe.getEmployee());
        }
        return List.copyOf(byId.values());
    }
}
