package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectForm;
import static com.projectsa.TestData.projectWaitingForEngineers;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.Notification;
import com.projectsa.notification.NotificationRepository;
import com.projectsa.notification.NotificationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** UC04 Assign Engineer to Project. */
@SpringBootTest
@Transactional
class AssignEngineerServiceTest {

    @Autowired
    AssignEngineerService service;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectEngineerRepository projectEngineers;
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
    Employee cara;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        otherPm = employee(employees, "Mia Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA", "Linux");
        ben = tech(employees, "Ben Tech", "ccna");
        cara = tech(employees, "Cara Tech", "Fortinet NSE4");
        project = projectWaitingForEngineers(projectService, projects, sale, presale);
    }

    List<Long> engineerIdsOnProject() {
        return projectEngineers.findForProject(project.getId()).stream().map(pe -> pe.getEmployee().getId()).toList();
    }

    @Test
    void pmAssignsEngineersBecomesPmInChargeAndEngineersAreNotified() {
        service.assign(project.getId(), List.of(anna.getId(), ben.getId()), user(pm));

        Project saved = projectService.detail(project.getId());
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.WORKING);
        assertThat(saved.getPm().getId()).isEqualTo(pm.getId());
        assertThat(engineerIdsOnProject()).containsExactlyInAnyOrder(anna.getId(), ben.getId());

        assertThat(notifications.findAll())
                .extracting(n -> n.getRecipient().getId(), Notification::getType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(anna.getId(), NotificationType.ASSIGNED),
                        org.assertj.core.groups.Tuple.tuple(ben.getId(), NotificationType.ASSIGNED));
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(anna.getId())).singleElement()
                .satisfies(n -> {
                    assertThat(n.getMessage()).contains("Network Upgrade").contains("Max Pm");
                    assertThat(n.getProject().getId()).isEqualTo(project.getId());
                    assertThat(n.getReadAt()).isNull();
                });
    }

    @Test
    void onlyPmCanAssign() {
        for (Employee e : List.of(sale, presale, anna)) {
            assertThatThrownBy(() -> service.assign(project.getId(), List.of(anna.getId()), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.page(project.getId(), user(e), null, null))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(projectEngineers.count()).isZero();
    }

    @Test
    void onlyWhileWaitingForEngineers() {
        Project fresh = projectService.create(projectForm(sale.getId(), presale.getId()), user(sale));
        assertThatThrownBy(() -> service.assign(fresh.getId(), List.of(anna.getId()), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("New Project");

        service.assign(project.getId(), List.of(anna.getId()), user(pm));
        assertThatThrownBy(() -> service.assign(project.getId(), List.of(ben.getId()), user(otherPm)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("Working");
        assertThat(engineerIdsOnProject()).containsExactly(anna.getId());
    }

    @Test
    void atLeastOneEngineer() {
        assertThatThrownBy(() -> service.assign(project.getId(), List.of(), user(pm)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "engineerIds")
                .hasMessageContaining("at least one");
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
    }

    @Test
    void onlyTechEngineersCanBeAssigned() {
        for (Long id : List.of(pm.getId(), sale.getId(), 999_999L)) {
            assertThatThrownBy(() -> service.assign(project.getId(), List.of(anna.getId(), id), user(pm)))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("Only Tech Engineers");
        }
        assertThat(projectEngineers.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void sameEngineerTwiceIsSavedOnce() {
        service.assign(project.getId(), List.of(anna.getId(), anna.getId()), user(pm));
        assertThat(engineerIdsOnProject()).containsExactly(anna.getId());
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void afterApprovedRequirementEngineersAreKeptAndPmCanConfirmOrAdd() {
        service.assign(project.getId(), List.of(anna.getId()), user(pm));
        // UC05 → UC06 → UC07 approve → UC02 again brings the project back to waiting for engineers
        Project p = projects.findById(project.getId()).orElseThrow();
        p.changeStatus(ProjectStatus.TESTING);
        p.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        p.changeStatus(ProjectStatus.NEW_PROJECT);
        p.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        projects.saveAndFlush(p);

        // Anna is shown as already assigned, not in the list to tick
        AssignEngineerService.AssignPage page = service.page(project.getId(), user(pm), null, null);
        assertThat(page.alreadyAssigned()).extracting(pe -> pe.getEmployee().getId()).containsExactly(anna.getId());
        assertThat(page.engineers()).extracting(Employee::getId).containsExactly(ben.getId(), cara.getId());

        // The PM in charge adds Ben (Anna again is ignored) and stays PM in charge
        List<Employee> added = service.assign(project.getId(), List.of(anna.getId(), ben.getId()), user(pm));
        assertThat(added).extracting(Employee::getId).containsExactly(ben.getId());
        assertThat(engineerIdsOnProject()).containsExactlyInAnyOrder(anna.getId(), ben.getId());
        assertThat(projectService.detail(project.getId()).getPm().getId()).isEqualTo(pm.getId());
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(anna.getId())).hasSize(1);
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(ben.getId())).hasSize(1);
    }

    @Test
    void afterApprovedRequirementOnlyThePmInChargeMayReconfirm() {
        service.assign(project.getId(), List.of(anna.getId()), user(pm));
        Project p = projects.findById(project.getId()).orElseThrow();
        p.changeStatus(ProjectStatus.TESTING);
        p.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        p.changeStatus(ProjectStatus.NEW_PROJECT);
        p.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        projects.saveAndFlush(p);

        assertThat(AssignEngineerService.canAssign(p, user(pm))).isTrue();
        assertThat(AssignEngineerService.canAssign(p, user(otherPm))).isFalse();
        assertThat(projectService.projectsNeedingEngineers(user(otherPm))).isEmpty();
        assertThat(projectService.projectsNeedingEngineers(user(pm))).extracting(Project::getId).containsExactly(project.getId());

        assertThatThrownBy(() -> service.page(project.getId(), user(otherPm), null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assign(project.getId(), List.of(), user(otherPm)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assign(project.getId(), List.of(ben.getId()), user(otherPm)))
                .isInstanceOf(AccessDeniedException.class);

        Project after = projectService.detail(project.getId());
        assertThat(after.getPm().getId()).isEqualTo(pm.getId());
        assertThat(after.getStatus()).isEqualTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        assertThat(engineerIdsOnProject()).containsExactly(anna.getId());
    }

    @Test
    void confirmingWithoutNewEngineersIsAllowedWhenSomeAreAlreadyAssigned() {
        service.assign(project.getId(), List.of(anna.getId()), user(pm));
        Project p = projects.findById(project.getId()).orElseThrow();
        p.changeStatus(ProjectStatus.TESTING);
        p.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        p.changeStatus(ProjectStatus.NEW_PROJECT);
        p.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        projects.saveAndFlush(p);

        assertThat(service.assign(project.getId(), List.of(), user(pm))).isEmpty();
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.WORKING);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void engineerListShowsEveryTechWithSkillsAndFilterIgnoresCase() {
        AssignEngineerService.AssignPage all = service.page(project.getId(), user(pm), null, null);
        assertThat(all.anyEngineer()).isTrue();
        assertThat(all.engineers()).extracting(Employee::getName).containsExactly("Anna Tech", "Ben Tech", "Cara Tech");
        assertThat(all.engineers().getFirst().getSkills()).hasSize(2);
        // "CCNA" and "ccna" are offered once
        assertThat(all.skills()).hasSize(3).anyMatch(s -> s.equalsIgnoreCase("ccna"))
                .contains("Fortinet NSE4", "Linux");

        assertThat(service.page(project.getId(), user(pm), " CcNa ", null).engineers())
                .extracting(Employee::getName).containsExactly("Anna Tech", "Ben Tech");
        assertThat(service.page(project.getId(), user(pm), "Linux", null).engineers())
                .extracting(Employee::getName).containsExactly("Anna Tech");
        assertThat(service.page(project.getId(), user(pm), "Docker", null).engineers()).isEmpty();
    }

    @Test
    void tickedEngineersStayVisibleWhenFilterChanges() {
        assertThat(service.page(project.getId(), user(pm), "Linux", List.of(cara.getId())).engineers())
                .extracting(Employee::getName).containsExactly("Anna Tech", "Cara Tech");
    }

    @Test
    void pageSaysWhenNoTechEngineerExists() {
        projectEngineers.deleteAll();
        employees.deleteAll(List.of(anna, ben, cara));
        employees.flush();
        AssignEngineerService.AssignPage page = service.page(project.getId(), user(pm), null, null);
        assertThat(page.anyEngineer()).isFalse();
        assertThat(page.engineers()).isEmpty();
    }
}
