package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectTesting;
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
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Complete Project over HTTP: button + confirmation on UC03, PM dashboard, access rules. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompleteProjectWebTest {

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

    Employee sale;
    Employee presale;
    Employee pm;
    Employee otherPm;
    Employee anna;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        otherPm = employee(employees, "Mia Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        project = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusDays(8), sale, presale, pm, anna);
    }

    @Test
    void pmInChargeSeesButtonWithConfirmationAndCompletes() throws Exception {
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canComplete", true))
                .andExpect(content().string(Matchers.containsString("completeModal")))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/complete")));
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("Complete Project")));

        mvc.perform(post("/projects/{id}/complete", project.getId()).with(csrf()).with(user(user(pm))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("is now Finished")));
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.FINISH);

        // Finished: no action buttons for anyone
        for (Employee e : new Employee[] {pm, sale, presale, anna}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(model().attribute("canComplete", false))
                    .andExpect(model().attribute("canAddRequirement", false))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("completeModal"))));
        }
    }

    @Test
    void beforeTheWeekIsOverOnlyAHintIsShown() throws Exception {
        Project recent = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusDays(2), sale, presale, pm, anna);
        mvc.perform(get("/projects/{id}", recent.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canComplete", false))
                .andExpect(content().string(Matchers.containsString("is available from")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("completeModal"))));
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("Can be completed from")));
        mvc.perform(post("/projects/{id}/complete", recent.getId()).with(csrf()).with(user(user(pm))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("one week after the customer test")));
        assertThat(projects.findById(recent.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
    }

    @Test
    void othersCannotCompleteOrSeeTheButton() throws Exception {
        for (Employee e : new Employee[] {otherPm, sale, presale, anna}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(model().attribute("canComplete", false))
                    .andExpect(model().attribute("completeFrom", Matchers.nullValue()));
            mvc.perform(post("/projects/{id}/complete", project.getId()).with(csrf()).with(user(user(e))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/projects/{id}/complete", project.getId()).with(user(user(pm))))   // no CSRF token
                .andExpect(status().isForbidden());
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
    }

    @Test
    void unknownProjectIs404() throws Exception {
        mvc.perform(post("/projects/{id}/complete", 999_999).with(csrf()).with(user(user(pm))))
                .andExpect(status().isNotFound());
    }
}
