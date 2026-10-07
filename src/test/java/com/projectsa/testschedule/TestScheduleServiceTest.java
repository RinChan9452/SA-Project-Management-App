package com.projectsa.testschedule;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectWaitingForEngineers;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.Notification;
import com.projectsa.notification.NotificationRepository;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineer;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** UC05 Set Project Test Calendar Notification. */
@SpringBootTest
@Transactional
class TestScheduleServiceTest {

    @Autowired
    TestScheduleService service;
    @Autowired
    ProjectService projectService;
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
    Employee anna;
    Employee ben;
    Employee outsider;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        ben = tech(employees, "Ben Tech", "Linux");
        outsider = tech(employees, "Olly Tech", "CCNA");
        project = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, anna, ben);
    }

    static TestScheduleForm form(LocalDateTime testAt, String detail) {
        TestScheduleForm f = new TestScheduleForm();
        f.setTestAt(testAt);
        f.setDetail(detail);
        return f;
    }

    static LocalDateTime inDays(int days) {
        return LocalDateTime.now().plusDays(days).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void assignedEngineerSetsTestDateProjectMovesToTestingAndEveryoneIsNotified() {
        LocalDateTime testAt = inDays(5);
        TestSchedule saved = service.save(project.getId(), form(testAt, "  UAT at customer site  "), user(anna));

        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
        assertThat(service.schedulesOf(project.getId())).singleElement().satisfies(t -> {
            assertThat(t.getId()).isEqualTo(saved.getId());
            assertThat(t.getTestAt()).isEqualTo(testAt);
            assertThat(t.getDetail()).isEqualTo("UAT at customer site");
            assertThat(t.getCreatedBy().getId()).isEqualTo(anna.getId());
            assertThat(t.getCreatedAt()).isNotNull();
        });

        // Sale, PM and both engineers: one now + one reminder each; Presale and outsiders get nothing
        List<Notification> all = notifications.findAll();
        assertThat(all).hasSize(8).extracting(n -> n.getRecipient().getId(), Notification::getType)
                .containsExactlyInAnyOrder(
                        tuple(sale.getId(), NotificationType.TEST_SCHEDULED), tuple(sale.getId(), NotificationType.TEST_REMINDER),
                        tuple(pm.getId(), NotificationType.TEST_SCHEDULED), tuple(pm.getId(), NotificationType.TEST_REMINDER),
                        tuple(anna.getId(), NotificationType.TEST_SCHEDULED), tuple(anna.getId(), NotificationType.TEST_REMINDER),
                        tuple(ben.getId(), NotificationType.TEST_SCHEDULED), tuple(ben.getId(), NotificationType.TEST_REMINDER));
        assertThat(all).allSatisfy(n -> {
            assertThat(n.getProject().getId()).isEqualTo(project.getId());
            assertThat(n.getMessage()).contains("Network Upgrade");
            assertThat(n.getReadAt()).isNull();
        });
        assertThat(all).filteredOn(n -> n.getType() == NotificationType.TEST_SCHEDULED)
                .allSatisfy(n -> {
                    assertThat(n.getSendAt()).isBeforeOrEqualTo(LocalDateTime.now());
                    assertThat(n.getMessage()).contains("Anna Tech");
                });
        assertThat(all).filteredOn(n -> n.getType() == NotificationType.TEST_REMINDER)
                .allSatisfy(n -> assertThat(n.getSendAt()).isEqualTo(testAt.minusDays(1)));
    }

    @Test
    void reminderForATestLessThanADayAwayIsNotInThePast() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 9, 0);
        assertThat(TestScheduleService.reminderAt(now.plusDays(3), now)).isEqualTo(now.plusDays(2));
        assertThat(TestScheduleService.reminderAt(now.plusHours(5), now)).isEqualTo(now);

        service.save(project.getId(), form(LocalDateTime.now().plusHours(3), "Quick retest"), user(anna));
        assertThat(notifications.findAll()).filteredOn(n -> n.getType() == NotificationType.TEST_REMINDER)
                .allSatisfy(n -> assertThat(n.getSendAt()).isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void onlyTechEngineers() {
        for (Employee e : List.of(sale, presale, pm)) {
            assertThatThrownBy(() -> service.save(project.getId(), form(inDays(2), "x"), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.projectForSchedule(project.getId(), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(schedules.count()).isZero();
    }

    @Test
    void onlyEngineersAssignedToTheProject() {
        assertThatThrownBy(() -> service.save(project.getId(), form(inDays(2), "x"), user(outsider)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("assigned");
        assertThatThrownBy(() -> service.projectForSchedule(project.getId(), user(outsider)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(schedules.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void onlyWhileWorking() {
        Project waiting = projectWaitingForEngineers(projectService, projects, sale, presale);
        projectEngineers.save(new ProjectEngineer(waiting, anna, pm));
        assertThatThrownBy(() -> service.save(waiting.getId(), form(inDays(2), "x"), user(anna)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("Waiting for Engineer Assignment");

        service.save(project.getId(), form(inDays(2), "first"), user(anna));
        assertThatThrownBy(() -> service.save(project.getId(), form(inDays(3), "second"), user(ben)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("Testing");
        assertThat(schedules.count()).isEqualTo(1);
    }

    @Test
    void testDateMustBeInTheFuture() {
        for (LocalDateTime when : List.of(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusDays(30))) {
            assertThatThrownBy(() -> service.save(project.getId(), form(when, "x"), user(anna)))
                    .isInstanceOf(FieldValidationException.class)
                    .hasFieldOrPropertyWithValue("field", "testAt")
                    .hasMessageContaining("future");
        }
        assertThatThrownBy(() -> service.validate(project.getId(), form(LocalDateTime.now().minusHours(1), null), user(anna)))
                .isInstanceOf(FieldValidationException.class);
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.WORKING);
        assertThat(schedules.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void dashboardListsNeedTestDateAndUpcomingTests() {
        assertThat(service.needTestDate(user(anna))).extracting(Project::getId).containsExactly(project.getId());
        assertThat(service.needTestDate(user(outsider))).isEmpty();
        assertThat(service.upcomingTests(user(anna))).isEmpty();

        LocalDateTime testAt = inDays(4);
        service.save(project.getId(), form(testAt, "UAT"), user(anna));

        assertThat(service.needTestDate(user(anna))).isEmpty();
        assertThat(service.upcomingTests(user(ben))).singleElement()
                .satisfies(t -> {
                    assertThat(t.getTestAt()).isEqualTo(testAt);
                    assertThat(t.getProject().getProjectName()).isEqualTo("Network Upgrade");
                });
        assertThat(service.upcomingTests(user(outsider))).isEmpty();
    }
}
