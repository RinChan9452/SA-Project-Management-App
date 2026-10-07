package com.projectsa.requirement;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectTesting;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** UC07 Approve New Project Requirement. */
@SpringBootTest
@Transactional
class ApprovalServiceTest {

    @Autowired
    ApprovalService service;
    @Autowired
    RequirementService requirementService;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectEngineerRepository projectEngineers;
    @Autowired
    TestScheduleRepository schedules;
    @Autowired
    RequirementRepository requirements;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    NotificationRepository notifications;

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;
    Employee anna;
    Employee ben;
    Project project;
    Requirement pending;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        ben = tech(employees, "Ben Tech", "Linux");
        project = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusHours(2), sale, presale, pm, anna, ben);
        pending = addRequirement(project, "Add guest Wi-Fi");
        notifications.deleteAll();   // only look at UC07's notifications
    }

    Requirement addRequirement(Project p, String title) {
        RequirementForm f = new RequirementForm();
        f.setTitle(title);
        f.setDetail("Customer wants a separate guest SSID");
        f.setPriority(Priority.HIGH);
        f.setDueDate(LocalDate.now().plusDays(7));
        return requirementService.save(p.getId(), f, user(pm));
    }

    ApprovalForm form(RequirementStatus decision, String reason) {
        ApprovalForm f = new ApprovalForm();
        f.setDecision(decision);
        f.setRejectReason(reason);
        f.setVersion(requirements.findById(pending.getId()).orElseThrow().getVersion());
        return f;
    }

    @Test
    void approveSendsProjectBackToNewProjectAndNotifiesPmPresaleAndEngineers() {
        Requirement decided = service.decide(pending.getId(), form(RequirementStatus.APPROVED, "ignored"), user(sale));

        Requirement r = requirements.findById(decided.getId()).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(RequirementStatus.APPROVED);
        assertThat(r.getRejectReason()).isNull();
        assertThat(r.getDecidedBy().getId()).isEqualTo(sale.getId());
        assertThat(r.getDecidedAt()).isBeforeOrEqualTo(LocalDateTime.now());

        Project p = projects.findById(project.getId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.NEW_PROJECT);
        assertThat(projectEngineers.findEngineerIds(project.getId()))   // engineers are kept (§8)
                .containsExactlyInAnyOrder(anna.getId(), ben.getId());

        assertThat(notifications.findAll())
                .extracting(n -> n.getRecipient().getId(), Notification::getType)
                .containsExactlyInAnyOrder(
                        tuple(pm.getId(), NotificationType.REQUIREMENT_APPROVED),
                        tuple(presale.getId(), NotificationType.REQUIREMENT_APPROVED),
                        tuple(anna.getId(), NotificationType.REQUIREMENT_APPROVED),
                        tuple(ben.getId(), NotificationType.REQUIREMENT_APPROVED));
        assertThat(notifications.findAll()).allSatisfy(n ->
                assertThat(n.getMessage()).contains("Add guest Wi-Fi", "approved", "Sam Sale"));
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(presale.getId())).singleElement()
                .satisfies(n -> assertThat(n.getMessage()).contains("please update the solution"));
        assertThat(service.pendingForSale(user(sale))).isEmpty();
    }

    @Test
    void rejectFinishesProjectStoresReasonAndNotifiesOnlyPm() {
        service.decide(pending.getId(), form(RequirementStatus.REJECTED, "  Over budget  "), user(sale));

        Requirement r = requirements.findById(pending.getId()).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(RequirementStatus.REJECTED);
        assertThat(r.getRejectReason()).isEqualTo("Over budget");
        assertThat(r.getDecidedBy().getId()).isEqualTo(sale.getId());
        assertThat(r.getDecidedAt()).isNotNull();
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.FINISH);

        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getRecipient().getId()).isEqualTo(pm.getId());
            assertThat(n.getType()).isEqualTo(NotificationType.REQUIREMENT_REJECTED);
            assertThat(n.getMessage()).contains("Add guest Wi-Fi", "rejected", "Reason: Over budget");
        });
    }

    @Test
    void veryLongReasonStillFitsTheNotification() {
        String reason = "x".repeat(1000);
        service.decide(pending.getId(), form(RequirementStatus.REJECTED, reason), user(sale));

        assertThat(requirements.findById(pending.getId()).orElseThrow().getRejectReason()).isEqualTo(reason);
        assertThat(notifications.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getMessage()).hasSize(1000).endsWith("…"));
    }

    @Test
    void rejectNeedsAReason() {
        for (String reason : new String[] {null, "", "   "}) {
            FieldValidationException e = catchThrowableOfType(FieldValidationException.class,
                    () -> service.decide(pending.getId(), form(RequirementStatus.REJECTED, reason), user(sale)));
            assertThat(e.getField()).isEqualTo("rejectReason");
        }
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.WAITING_FOR_APPROVE);
    }

    @Test
    void pendingIsNotADecision() {
        FieldValidationException e = catchThrowableOfType(FieldValidationException.class,
                () -> service.decide(pending.getId(), form(RequirementStatus.PENDING, null), user(sale)));
        assertThat(e.getField()).isEqualTo("decision");
    }

    @Test
    void onlyTheSaleInChargeMayDecide() {
        for (Employee e : new Employee[] {otherSale, presale, pm, anna}) {
            assertThatThrownBy(() -> service.decide(pending.getId(), form(RequirementStatus.APPROVED, null), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
        assertThat(notifications.count()).isZero();
    }

    @Test
    void noSecondDecision() {
        ApprovalForm approve = form(RequirementStatus.APPROVED, null);
        service.decide(pending.getId(), approve, user(sale));

        assertThatThrownBy(() -> service.decide(pending.getId(), approve, user(sale)))
                .isInstanceOf(ActionNotAllowedException.class)
                .hasMessageContaining("already approved by Sam Sale");
        assertThatThrownBy(() -> service.requirementForDecision(pending.getId(), user(sale)))
                .isInstanceOf(ActionNotAllowedException.class);
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.APPROVED);
    }

    @Test
    void stalePageIsRefused() {
        ApprovalForm f = form(RequirementStatus.APPROVED, null);
        f.setVersion(f.getVersion() + 1);
        assertThatThrownBy(() -> service.decide(pending.getId(), f, user(sale)))
                .isInstanceOf(ActionNotAllowedException.class)
                .hasMessageContaining("just changed");
        f.setVersion(null);
        assertThatThrownBy(() -> service.decide(pending.getId(), f, user(sale)))
                .isInstanceOf(ActionNotAllowedException.class);
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
    }

    @Test
    void pendingListShowsOnlyMyProjectsOldestFirst() {
        Project second = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusHours(1), sale, presale, pm, anna);
        Requirement later = addRequirement(second, "Extra camera");
        Project othersProject = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusHours(1), otherSale, presale, pm, anna);
        addRequirement(othersProject, "Not mine");

        List<Requirement> mine = service.pendingForSale(user(sale));
        assertThat(mine).extracting(Requirement::getId).containsExactly(pending.getId(), later.getId());
        assertThat(service.pendingForSale(user(otherSale))).extracting(Requirement::getTitle).containsExactly("Not mine");
    }

    @Test
    void canDecideOnlyForSaleInChargeWhilePending() {
        Requirement r = requirements.findForDecision(pending.getId()).orElseThrow();
        Project p = r.getProject();
        assertThat(ApprovalService.canDecide(r, p, user(sale))).isTrue();
        assertThat(ApprovalService.canDecide(r, p, user(otherSale))).isFalse();
        assertThat(ApprovalService.canDecide(r, p, user(pm))).isFalse();
    }
}
