package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectTesting;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.Notification;
import com.projectsa.notification.NotificationRepository;
import com.projectsa.notification.NotificationType;
import com.projectsa.requirement.Priority;
import com.projectsa.requirement.RequirementForm;
import com.projectsa.requirement.RequirementService;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** Complete Project: only the PM in charge, only TESTING, only one week after the customer test (§8). */
@SpringBootTest
@Transactional
class CompleteProjectServiceTest {

    @Autowired
    CompleteProjectService service;
    @Autowired
    ProjectService projectService;
    @Autowired
    RequirementService requirementService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectEngineerRepository projectEngineers;
    @Autowired
    TestScheduleRepository schedules;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    NotificationRepository notifications;

    Employee sale;
    Employee presale;
    Employee pm;
    Employee otherPm;
    Employee anna;
    Employee ben;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        otherPm = employee(employees, "Mia Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        ben = tech(employees, "Ben Tech", "Linux");
        project = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusDays(8), sale, presale, pm, anna, ben);
    }

    @Test
    void pmInChargeCompletesAWeekAfterTheTestAndEveryoneIsNotified() {
        service.complete(project.getId(), user(pm));

        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.FINISH);
        assertThat(notifications.findAll())
                .extracting(n -> n.getRecipient().getId(), Notification::getType)
                .containsExactlyInAnyOrder(
                        tuple(sale.getId(), NotificationType.PROJECT_COMPLETED),
                        tuple(presale.getId(), NotificationType.PROJECT_COMPLETED),
                        tuple(anna.getId(), NotificationType.PROJECT_COMPLETED),
                        tuple(ben.getId(), NotificationType.PROJECT_COMPLETED));
        assertThat(notifications.findAll()).allSatisfy(n ->
                assertThat(n.getMessage()).contains("Network Upgrade", "Max Pm", "Finished"));
    }

    @Test
    void notBeforeOneWeekAfterTheTest() {
        for (LocalDateTime testAt : new LocalDateTime[] {
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1), LocalDateTime.now().minusDays(6)}) {
            Project p = projectTesting(projectService, projects, projectEngineers, schedules, testAt,
                    sale, presale, pm, anna);
            assertThatThrownBy(() -> service.complete(p.getId(), user(pm)))
                    .isInstanceOf(ActionNotAllowedException.class)
                    .hasMessageContaining("one week after the customer test");
            assertThat(projects.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
        }
        assertThat(notifications.count()).isZero();
    }

    @Test
    void weekIsCountedFromTheExactTestTime() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 10, 0);
        assertThat(CompleteProjectService.isWindowOver(now.minusDays(7), now)).isTrue();
        assertThat(CompleteProjectService.isWindowOver(now.minusDays(7).plusMinutes(1), now)).isFalse();
        assertThat(CompleteProjectService.isWindowOver(null, now)).isFalse();
        assertThat(CompleteProjectService.completeFrom(now)).isEqualTo(now.plusDays(7));
    }

    @Test
    void onlyThePmInCharge() {
        for (Employee e : new Employee[] {otherPm, sale, presale, anna}) {
            assertThatThrownBy(() -> service.complete(project.getId(), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
    }

    @Test
    void onlyFromTesting() {
        Project working = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, anna);
        assertThatThrownBy(() -> service.complete(working.getId(), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class)
                .hasMessageContaining("only be completed while it is \"Testing\"");

        // A new requirement within the week moves the project on, so it can no longer be completed
        RequirementForm f = new RequirementForm();
        f.setTitle("More ports");
        f.setDetail("Customer wants 10 more ports");
        f.setPriority(Priority.LOW);
        f.setDueDate(LocalDate.now());
        requirementService.save(project.getId(), f, user(pm));
        assertThatThrownBy(() -> service.complete(project.getId(), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class);
    }

    @Test
    void finishedProjectIsReadOnly() {
        service.complete(project.getId(), user(pm));
        assertThatThrownBy(() -> service.complete(project.getId(), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class);
        assertThatThrownBy(() -> requirementService.projectForRequirement(project.getId(), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class);
    }

    @Test
    void canCompleteForButtons() {
        LocalDateTime testAt = schedules.findLatestTestAt(project.getId());
        Project p = projects.findDetailById(project.getId()).orElseThrow();
        assertThat(CompleteProjectService.canComplete(p, testAt, user(pm))).isTrue();
        assertThat(CompleteProjectService.canComplete(p, testAt, user(otherPm))).isFalse();
        assertThat(CompleteProjectService.canComplete(p, testAt, user(sale))).isFalse();
        assertThat(CompleteProjectService.canComplete(p, LocalDateTime.now().minusDays(1), user(pm))).isFalse();
    }
}
