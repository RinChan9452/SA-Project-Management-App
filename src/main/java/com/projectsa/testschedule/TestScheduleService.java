package com.projectsa.testschedule;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineer;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC05 Set Project Test Calendar Notification — only engineers assigned to the project, only in WORKING. */
@Service
public class TestScheduleService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final ProjectRepository projects;
    private final ProjectEngineerRepository projectEngineers;
    private final TestScheduleRepository schedules;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public TestScheduleService(ProjectRepository projects, ProjectEngineerRepository projectEngineers,
                               TestScheduleRepository schedules, EmployeeRepository employees,
                               NotificationService notifications) {
        this.projects = projects;
        this.projectEngineers = projectEngineers;
        this.schedules = schedules;
        this.employees = employees;
        this.notifications = notifications;
    }

    /** UC05 step 1: my assigned projects that wait for a test date. */
    @Transactional(readOnly = true)
    public List<Project> needTestDate(CurrentUser user) {
        return projectEngineers.findProjectsOfEngineer(user.getId()).stream()
                .filter(p -> p.getStatus() == ProjectStatus.WORKING)
                .toList();
    }

    /** Tests still to come on my assigned projects, soonest first (TECH dashboard). */
    @Transactional(readOnly = true)
    public List<TestSchedule> upcomingTests(CurrentUser user) {
        return schedules.findUpcomingForEngineer(user.getId(), LocalDateTime.now(), ProjectStatus.TESTING);
    }

    /** UC03: every test schedule of the project, latest first. */
    @Transactional(readOnly = true)
    public List<TestSchedule> schedulesOf(Long projectId) {
        return schedules.findForProject(projectId);
    }

    /** Loads the project for the UC05 form after checking role, assignment and state. */
    @Transactional(readOnly = true)
    public Project projectForSchedule(Long projectId, CurrentUser user) {
        Project project = projects.findDetailById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        checkAllowed(project, user);
        return project;
    }

    /**
     * UC05 steps 3–4: saves the schedule, moves the project to TESTING and notifies the Sale in charge, the PM in
     * charge and every assigned engineer twice: right away, and a reminder shown from 1 day before the test.
     *
     * @throws FieldValidationException  on {@code testAt} if the date/time is not in the future
     * @throws ActionNotAllowedException if the project is not WORKING (or was just changed by someone else)
     */
    @Transactional
    public TestSchedule save(Long projectId, TestScheduleForm form, CurrentUser user) {
        Project project = projectForSchedule(projectId, user);
        check(form);

        TestSchedule schedule = schedules.save(new TestSchedule(project, form.getTestAt(), form.getDetail().trim(),
                employees.getReferenceById(user.getId())));
        project.changeStatus(ProjectStatus.TESTING);
        try {
            projects.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Another engineer set the test date of this project at the same moment
            throw new ActionNotAllowedException("This project was just changed by someone else. Please open it again.");
        }

        String when = form.getTestAt().format(WHEN);
        String name = "project \"" + project.getProjectName() + "\" (ID " + project.getId() + ")";
        String scheduled = "Customer test for " + name + " is scheduled on " + when + " by " + user.getName() + ".";
        String reminder = "Reminder: the customer test for " + name + " is on " + when + ".";
        LocalDateTime reminderAt = reminderAt(form.getTestAt(), LocalDateTime.now());
        for (Employee recipient : recipients(project)) {
            notifications.notify(recipient, project, NotificationType.TEST_SCHEDULED, scheduled);
            notifications.notifyAt(recipient, project, NotificationType.TEST_REMINDER, reminder, reminderAt);
        }
        return schedule;
    }

    /**
     * UC05 business rules only, so the form can show them together with its own field errors.
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validate(Long projectId, TestScheduleForm form, CurrentUser user) {
        projectForSchedule(projectId, user);
        check(form);
    }

    /** True when the user may open UC05 for this project (for the button on UC03). */
    public static boolean canSetTestDate(Project project, Collection<ProjectEngineer> engineers, CurrentUser user) {
        return canSetTestDate(project, engineers.stream().anyMatch(pe -> pe.getId().getEmployeeId().equals(user.getId())),
                user);
    }

    /** Same rule when it is already known whether the user is assigned to the project (F3 list). */
    public static boolean canSetTestDate(Project project, boolean assigned, CurrentUser user) {
        return user.getRole() == Role.TECH && project.getStatus() == ProjectStatus.WORKING && assigned;
    }

    /** 1 day before the test; when the test is less than a day away, right away so it is not lost. */
    static LocalDateTime reminderAt(LocalDateTime testAt, LocalDateTime now) {
        LocalDateTime dayBefore = testAt.minusDays(1);
        return dayBefore.isBefore(now) ? now : dayBefore;
    }

    private void check(TestScheduleForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getTestAt() != null && !form.getTestAt().isAfter(LocalDateTime.now())) {
            errors.add(new FieldMessage("testAt", "Test date and time must be in the future"));
        }
        FieldValidationException.throwIfAny(errors);
    }

    private void checkAllowed(Project project, CurrentUser user) {
        if (user == null || user.getRole() != Role.TECH) {
            throw new AccessDeniedException("Only a Tech Engineer can set the test date");
        }
        if (!projectEngineers.existsById(new ProjectEngineer.Key(project.getId(), user.getId()))) {
            throw new AccessDeniedException("Only engineers assigned to this project can set its test date");
        }
        if (project.getStatus() != ProjectStatus.WORKING) {
            throw new ActionNotAllowedException("The test date can only be set while the project is \""
                    + ProjectStatus.WORKING.getLabel() + "\". This project is \"" + project.getStatus().getLabel() + "\".");
        }
    }

    /** Sale in charge, PM in charge and assigned engineers, each once (§5b UC05 step 5). */
    private List<Employee> recipients(Project project) {
        Map<Long, Employee> byId = new LinkedHashMap<>();
        byId.put(project.getSale().getId(), project.getSale());
        if (project.getPm() != null) {
            byId.putIfAbsent(project.getPm().getId(), project.getPm());
        }
        for (ProjectEngineer pe : projectEngineers.findForProject(project.getId())) {
            byId.putIfAbsent(pe.getEmployee().getId(), pe.getEmployee());
        }
        return List.copyOf(byId.values());
    }
}
