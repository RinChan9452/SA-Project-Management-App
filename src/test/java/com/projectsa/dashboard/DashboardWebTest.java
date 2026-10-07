package com.projectsa.dashboard;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectWaitingForEngineers;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.Notification;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** F1 parts added with F2: SALE counts by status, latest unread notifications on every dashboard. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectEngineerRepository projectEngineers;
    @Autowired
    NotificationService notificationService;

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;
    Employee anna;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = employee(employees, "Anna Tech", Role.TECH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saleSeesCountsOfOwnProjectsByStatusLinkingToTheList() throws Exception {
        projectService.create(com.projectsa.TestData.projectForm(sale.getId(), presale.getId()), user(sale));
        projectService.create(com.projectsa.TestData.projectForm(sale.getId(), presale.getId()), user(sale));
        projectWaitingForEngineers(projectService, projects, sale, presale);
        projectWorking(projectService, projects, projectEngineers, sale, presale, pm, anna);
        projectWorking(projectService, projects, projectEngineers, otherSale, presale, pm, anna);   // not mine

        Map<ProjectStatus, Long> counts = (Map<ProjectStatus, Long>) mvc.perform(get("/dashboard").with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString(
                        "/projects?saleId=" + sale.getId() + "&amp;status=NEW_PROJECT")))
                .andExpect(content().string(Matchers.containsString(
                        "/projects?saleId=" + sale.getId() + "&amp;status=FINISH")))
                .andReturn().getModelAndView().getModel().get("statusCounts");
        assertThat(counts.keySet()).containsExactly(ProjectStatus.values());
        assertThat(counts).containsEntry(ProjectStatus.NEW_PROJECT, 2L)
                .containsEntry(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER, 1L)
                .containsEntry(ProjectStatus.WORKING, 1L)
                .containsEntry(ProjectStatus.TESTING, 0L)
                .containsEntry(ProjectStatus.WAITING_FOR_APPROVE, 0L)
                .containsEntry(ProjectStatus.FINISH, 0L);
    }

    @Test
    void otherRolesHaveNoStatusCounts() throws Exception {
        for (Employee e : new Employee[] {presale, pm, anna}) {
            mvc.perform(get("/dashboard").with(user(user(e))))
                    .andExpect(model().attributeDoesNotExist("statusCounts"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDashboardShowsMyLatestFiveUnreadNotifications() throws Exception {
        Project project = projectWaitingForEngineers(projectService, projects, sale, presale);
        for (Employee e : new Employee[] {sale, presale, pm, anna}) {
            for (int i = 1; i <= 6; i++) {
                notificationService.notifyAt(e, project, NotificationType.ASSIGNED, e.getName() + " note " + i,
                        LocalDateTime.now().minusHours(10 - i));
            }
            Notification read = notificationService.notifyAt(e, project, NotificationType.ASSIGNED, "read note",
                    LocalDateTime.now().minusSeconds(1));
            notificationService.open(read.getId(), user(e));
            notificationService.notifyAt(e, project, NotificationType.TEST_REMINDER, "future note",
                    LocalDateTime.now().plusDays(1));
        }

        for (Employee e : new Employee[] {sale, presale, pm, anna}) {
            List<Notification> latest = (List<Notification>) mvc.perform(get("/dashboard").with(user(user(e))))
                    .andExpect(content().string(Matchers.containsString("Latest notifications")))
                    .andExpect(content().string(Matchers.containsString(e.getName() + " note 6")))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("read note"))))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("future note"))))
                    .andReturn().getModelAndView().getModel().get("latestNotifications");
            assertThat(latest).extracting(Notification::getMessage).containsExactly(
                    e.getName() + " note 6", e.getName() + " note 5", e.getName() + " note 4",
                    e.getName() + " note 3", e.getName() + " note 2");
        }
    }

    @Test
    void noNewNotificationsMessage() throws Exception {
        mvc.perform(get("/dashboard").with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("No new notifications.")));
    }
}
