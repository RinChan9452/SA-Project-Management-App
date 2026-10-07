package com.projectsa.requirement;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectTesting;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.document.ProjectDocument;
import com.projectsa.document.ProjectDocumentRepository;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import com.projectsa.testschedule.TestScheduleRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** UC06 over HTTP, plus what UC03, the PM dashboard and downloads show afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequirementWebTest {

    static final byte[] PDF = "%PDF-1.4\nchange request".getBytes(StandardCharsets.US_ASCII);

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
    RequirementRepository requirements;
    @Autowired
    ProjectDocumentRepository documents;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager em;

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;
    Employee otherPm;
    Employee anna;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        otherPm = employee(employees, "Mia Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        project = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusHours(1), sale, presale, pm, anna);
    }

    MockMultipartHttpServletRequestBuilder requirementPost(Long projectId) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/requirements", projectId)
                .param("title", "Add guest Wi-Fi")
                .param("detail", "Customer wants a separate guest SSID")
                .param("priority", "HIGH")
                .param("dueDate", LocalDate.now().plusDays(14).toString())
                .param("relatedFeature", "Wireless")
                .with(csrf());
    }

    @Test
    void pmInChargeSeesButtonOnDetailAndDashboard() throws Exception {
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canAddRequirement", true))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/requirements/new")))
                .andExpect(content().string(Matchers.containsString("No new requirements")));
        for (Employee e : new Employee[] {otherPm, sale, presale, anna}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(model().attribute("canAddRequirement", false));
        }
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("My projects in Testing")))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/requirements/new")))
                .andExpect(content().string(Matchers.containsString("You have not added any customer requirement yet")));
        mvc.perform(get("/projects/{id}/requirements/new", project.getId()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Earlier requirements")))
                .andExpect(content().string(Matchers.containsString("Anna Tech")))
                .andExpect(content().string(Matchers.containsString("enctype=\"multipart/form-data\"")));
    }

    @Test
    void buttonHiddenAndFormRefusedBeforeTheCustomerTest() throws Exception {
        Project upcoming = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().plusDays(2), sale, presale, pm, anna);
        mvc.perform(get("/projects/{id}", upcoming.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canAddRequirement", false));
        mvc.perform(get("/projects/{id}/requirements/new", upcoming.getId()).with(user(user(pm))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("after the customer test")));
        mvc.perform(requirementPost(upcoming.getId()).with(user(user(pm))))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(requirements.count()).isZero();
    }

    @Test
    void otherRolesAndOtherPmsCannotOpenOrPost() throws Exception {
        for (Employee e : new Employee[] {sale, presale, anna, otherPm}) {
            mvc.perform(get("/projects/{id}/requirements/new", project.getId()).with(user(user(e))))
                    .andExpect(status().isForbidden());
            mvc.perform(requirementPost(project.getId()).with(user(user(e))))
                    .andExpect(status().isForbidden());
        }
        assertThat(requirements.count()).isZero();
    }

    @Test
    void saveThenSaleSeesItAndPmDashboardShowsIt() throws Exception {
        mvc.perform(requirementPost(project.getId())
                        .file(new MockMultipartFile("attachments", "layout.pdf", "application/pdf", PDF))
                        .with(user(user(pm))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("Add guest Wi-Fi")));

        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.WAITING_FOR_APPROVE);
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString("Customer Requirements")))
                .andExpect(content().string(Matchers.containsString("Add guest Wi-Fi")))
                .andExpect(content().string(Matchers.containsString("Customer wants a separate guest SSID")))
                .andExpect(content().string(Matchers.containsString("Related feature: Wireless")))
                .andExpect(content().string(Matchers.containsString("Pending")))
                .andExpect(content().string(Matchers.containsString("High")))
                .andExpect(content().string(Matchers.containsString("layout.pdf")));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(pm))))
                .andExpect(model().attribute("canAddRequirement", false));
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("None of your projects is in Testing")))
                .andExpect(content().string(Matchers.containsString("My requirements")))
                .andExpect(content().string(Matchers.containsString("Add guest Wi-Fi")));
    }

    @Test
    void pendingAttachmentsOnlyForSaleAndPmInChargeApprovedOnesForEveryone() throws Exception {
        mvc.perform(requirementPost(project.getId())
                .file(new MockMultipartFile("attachments", "layout.pdf", "application/pdf", PDF))
                .with(user(user(pm))));
        ProjectDocument doc = documents.findAttachments(project.getId()).getFirst();

        for (Employee e : new Employee[] {sale, pm}) {
            mvc.perform(get("/documents/{id}", doc.getId()).with(user(user(e)))).andExpect(status().isOk());
        }
        for (Employee e : new Employee[] {otherSale, presale, anna, otherPm}) {
            mvc.perform(get("/documents/{id}", doc.getId()).with(user(user(e)))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("1 file(s), shown after approval")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/documents/" + doc.getId()))));

        // Once approved (UC07), the attachment is a public document on UC03
        jdbc.update("update requirement set status = 'APPROVED' where project_id = ?", project.getId());
        em.clear();
        mvc.perform(get("/documents/{id}", doc.getId()).with(user(user(anna)))).andExpect(status().isOk());
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("/documents/" + doc.getId())))
                .andExpect(content().string(Matchers.containsString("Requirement Attachment")));
    }

    @Test
    void missingFieldsAndPastDueDateShowTogether() throws Exception {
        mvc.perform(multipart("/projects/{id}/requirements", project.getId())
                        .param("title", " ").param("detail", "").param("priority", "")
                        .param("dueDate", LocalDate.now().minusDays(1).toString())
                        .with(csrf()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "title", "detail", "priority", "dueDate"))
                .andExpect(content().string(Matchers.containsString("Title is required")))
                .andExpect(content().string(Matchers.containsString("Detail is required")))
                .andExpect(content().string(Matchers.containsString("Please choose a priority")))
                .andExpect(content().string(Matchers.containsString("Customer due date cannot be in the past")));
        assertThat(requirements.count()).isZero();
    }

    @Test
    void badAttachmentAndTamperedValuesShowPlainMessages() throws Exception {
        mvc.perform(multipart("/projects/{id}/requirements", project.getId())
                        .file(new MockMultipartFile("attachments", "virus.exe", "application/pdf", "MZ...".getBytes()))
                        .param("title", "x").param("detail", "y").param("priority", "URGENT").param("dueDate", "soon")
                        .with(csrf()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Please choose a priority")))
                .andExpect(content().string(Matchers.containsString("Please enter a valid due date")))
                .andExpect(content().string(Matchers.containsString("virus.exe: Only PDF, PNG, JPG files are allowed")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
        assertThat(requirements.count()).isZero();
        assertThat(documents.count()).isZero();
    }

    @Test
    void unknownProjectIs404() throws Exception {
        mvc.perform(get("/projects/{id}/requirements/new", 999_999).with(user(user(pm))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(multipart("/projects/{id}/requirements", project.getId())
                        .param("title", "x").param("detail", "y").param("priority", "LOW")
                        .param("dueDate", LocalDate.now().toString()).with(user(user(pm))))
                .andExpect(status().isForbidden());
        assertThat(requirements.count()).isZero();
    }
}
