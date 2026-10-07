package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectWaitingForEngineers;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** UC04 over HTTP, plus what UC03 and the dashboards show afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssignEngineerWebTest {

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

    Employee sale;
    Employee presale;
    Employee pm;
    Employee anna;
    Employee ben;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA", "Linux");
        ben = tech(employees, "Ben Tech", "Fortinet NSE4");
        project = projectWaitingForEngineers(projectService, projects, sale, presale);
    }

    @Test
    void pmSeesProjectOnDashboardAndButtonOnDetail() throws Exception {
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("Need engineers")))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/assign")));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canAssignEngineers", true));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(model().attribute("canAssignEngineers", false));
    }

    @Test
    void pageListsEngineersWithSkillsAndFilters() throws Exception {
        mvc.perform(get("/projects/{id}/assign", project.getId()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Anna Tech")))
                .andExpect(content().string(Matchers.containsString("Ben Tech")))
                .andExpect(content().string(Matchers.containsString("Fortinet NSE4")));
        mvc.perform(get("/projects/{id}/assign", project.getId()).param("skill", "linux").with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Anna Tech")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Ben Tech"))));
    }

    @Test
    void otherRolesCannotOpenOrPost() throws Exception {
        for (Employee e : new Employee[] {sale, presale, anna}) {
            mvc.perform(get("/projects/{id}/assign", project.getId()).with(user(user(e))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/projects/{id}/assign", project.getId()).param("engineerIds", anna.getId().toString())
                            .with(csrf()).with(user(user(e))))
                    .andExpect(status().isForbidden());
        }
        assertThat(projectEngineers.count()).isZero();
    }

    @Test
    void assignThenEveryoneSeesEngineersAndDashboardsUpdate() throws Exception {
        mvc.perform(post("/projects/{id}/assign", project.getId())
                        .param("engineerIds", anna.getId().toString(), ben.getId().toString())
                        .with(csrf()).with(user(user(pm))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("Anna Tech")));

        // UC03: assigned engineers, PM in charge and new status, for any role
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString("Assigned Engineers")))
                .andExpect(content().string(Matchers.containsString("Anna Tech")))
                .andExpect(content().string(Matchers.containsString("Ben Tech")))
                .andExpect(content().string(Matchers.containsString("Max Pm")))
                .andExpect(content().string(Matchers.containsString("Working")));

        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("No project is waiting for engineers")))
                .andExpect(content().string(Matchers.containsString("My projects (PM in charge)")))
                .andExpect(content().string(Matchers.containsString("Network Upgrade")));
        mvc.perform(get("/dashboard").with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("My assigned projects")))
                .andExpect(content().string(Matchers.containsString("Network Upgrade")));
    }

    @Test
    void nobodySelectedShowsErrorOnPage() throws Exception {
        mvc.perform(post("/projects/{id}/assign", project.getId()).with(csrf()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "engineerIds"))
                .andExpect(content().string(Matchers.containsString("Please select at least one engineer")));
    }

    @Test
    void tamperedIdShowsPlainMessage() throws Exception {
        mvc.perform(post("/projects/{id}/assign", project.getId()).param("engineerIds", "abc")
                        .with(csrf()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
        assertThat(projectEngineers.count()).isZero();
    }

    @Test
    void wrongStateShowsMessageOnDashboard() throws Exception {
        mvc.perform(post("/projects/{id}/assign", project.getId()).param("engineerIds", anna.getId().toString())
                .with(csrf()).with(user(user(pm))));
        mvc.perform(get("/projects/{id}/assign", project.getId()).with(user(user(pm))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("Working")));
    }

    @Test
    void anotherPmCannotTakeOverAfterApprovedRequirement() throws Exception {
        Employee otherPm = employee(employees, "Mia Pm", Role.PM);
        mvc.perform(post("/projects/{id}/assign", project.getId()).param("engineerIds", anna.getId().toString())
                .with(csrf()).with(user(user(pm))));
        Project p = projects.findById(project.getId()).orElseThrow();
        p.changeStatus(ProjectStatus.TESTING);
        p.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        p.changeStatus(ProjectStatus.NEW_PROJECT);
        p.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        projects.saveAndFlush(p);

        mvc.perform(get("/dashboard").with(user(user(otherPm))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/projects/" + project.getId() + "/assign"))));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(otherPm))))
                .andExpect(model().attribute("canAssignEngineers", false));
        mvc.perform(get("/projects/{id}/assign", project.getId()).with(user(user(otherPm))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/projects/{id}/assign", project.getId()).with(csrf()).with(user(user(otherPm))))
                .andExpect(status().isForbidden());

        // The original PM still sees the button and can re-confirm
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canAssignEngineers", true));
        mvc.perform(post("/projects/{id}/assign", project.getId()).with(csrf()).with(user(user(pm))))
                .andExpect(redirectedUrl("/projects/" + project.getId()));
        assertThat(projects.findById(project.getId()).orElseThrow().getPm().getId()).isEqualTo(pm.getId());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/projects/{id}/assign", project.getId()).param("engineerIds", anna.getId().toString())
                        .with(user(user(pm))))
                .andExpect(status().isForbidden());
    }
}
