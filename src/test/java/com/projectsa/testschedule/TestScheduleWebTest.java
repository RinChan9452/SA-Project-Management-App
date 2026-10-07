package com.projectsa.testschedule;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationRepository;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** UC05 over HTTP, plus what UC03 and the dashboards show afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TestScheduleWebTest {

    /** What {@code <input type="datetime-local">} sends. */
    static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

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
    TestScheduleRepository schedules;
    @Autowired
    NotificationRepository notifications;

    Employee sale;
    Employee presale;
    Employee pm;
    Employee anna;
    Employee outsider;
    Project project;
    LocalDateTime testAt;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        outsider = tech(employees, "Olly Tech", "CCNA");
        project = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, anna);
        testAt = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void assignedEngineerSeesProjectOnDashboardAndButtonOnDetail() throws Exception {
        mvc.perform(get("/dashboard").with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("Need test date")))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/test-schedule")))
                .andExpect(content().string(Matchers.containsString("No upcoming customer tests")));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(model().attribute("canSetTestDate", true))
                .andExpect(content().string(Matchers.containsString("No customer test date set yet")));
        for (Employee e : new Employee[] {outsider, pm, sale}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(model().attribute("canSetTestDate", false));
        }
        mvc.perform(get("/projects/{id}/test-schedule", project.getId()).with(user(user(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("type=\"datetime-local\"")));
    }

    @Test
    void otherRolesAndUnassignedEngineersCannotOpenOrPost() throws Exception {
        for (Employee e : new Employee[] {sale, presale, pm, outsider}) {
            mvc.perform(get("/projects/{id}/test-schedule", project.getId()).with(user(user(e))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/projects/{id}/test-schedule", project.getId())
                            .param("testAt", testAt.format(INPUT)).param("detail", "UAT")
                            .with(csrf()).with(user(user(e))))
                    .andExpect(status().isForbidden());
        }
        assertThat(schedules.count()).isZero();
    }

    @Test
    void saveThenEveryoneSeesScheduleAndDashboardsUpdate() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId())
                        .param("testAt", testAt.format(INPUT)).param("detail", "UAT with customer IT team")
                        .with(csrf()).with(user(user(anna))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("Testing")));

        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
        assertThat(notifications.count()).isEqualTo(6);   // Sale, PM, Anna × (now + reminder)

        String shown = testAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH));
        // UC03: Sale and PM can see the schedule (CRUD: R on SetTestDate)
        for (Employee e : new Employee[] {sale, pm}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(content().string(Matchers.containsString("Test Schedule")))
                    .andExpect(content().string(Matchers.containsString(shown)))
                    .andExpect(content().string(Matchers.containsString("UAT with customer IT team")))
                    .andExpect(content().string(Matchers.containsString("Upcoming")));
        }
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(model().attribute("canSetTestDate", false));

        mvc.perform(get("/dashboard").with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("No project is waiting for a test date")))
                .andExpect(content().string(Matchers.containsString("Upcoming tests")))
                .andExpect(content().string(Matchers.containsString(shown)));
    }

    @Test
    void missingFieldsShowTogether() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId()).param("testAt", "").param("detail", " ")
                        .with(csrf()).with(user(user(anna))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "testAt", "detail"))
                .andExpect(content().string(Matchers.containsString("Test date and time is required")))
                .andExpect(content().string(Matchers.containsString("Test details are required")));
    }

    @Test
    void pastDateAndBlankDetailShowTogether() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId())
                        .param("testAt", LocalDateTime.now().minusDays(1).format(INPUT)).param("detail", "")
                        .with(csrf()).with(user(user(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Test date and time must be in the future")))
                .andExpect(content().string(Matchers.containsString("Test details are required")));
        assertThat(schedules.count()).isZero();
    }

    @Test
    void invalidDateShowsPlainMessage() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId()).param("testAt", "next tuesday")
                        .param("detail", "UAT").with(csrf()).with(user(user(anna))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Please enter a valid test date and time")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
    }

    @Test
    void wrongStateShowsMessageOnDashboard() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId())
                .param("testAt", testAt.format(INPUT)).param("detail", "UAT").with(csrf()).with(user(user(anna))));
        mvc.perform(get("/projects/{id}/test-schedule", project.getId()).with(user(user(anna))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("Testing")));
    }

    @Test
    void unknownProjectIs404() throws Exception {
        mvc.perform(get("/projects/{id}/test-schedule", 999_999).with(user(user(anna))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/projects/{id}/test-schedule", project.getId())
                        .param("testAt", testAt.format(INPUT)).param("detail", "UAT").with(user(user(anna))))
                .andExpect(status().isForbidden());
        assertThat(schedules.count()).isZero();
    }
}
