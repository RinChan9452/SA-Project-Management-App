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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** UC07 over HTTP, plus what the SALE dashboard, UC03 and downloads show afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalWebTest {

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

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;
    Employee anna;
    Project project;
    Requirement pending;

    @BeforeEach
    void setUp() throws Exception {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        anna = tech(employees, "Anna Tech", "CCNA");
        project = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusHours(1), sale, presale, pm, anna);
        mvc.perform(multipart("/projects/{id}/requirements", project.getId())
                .file(new MockMultipartFile("attachments", "layout.pdf", "application/pdf", PDF))
                .param("title", "Add guest Wi-Fi").param("detail", "Customer wants a separate guest SSID")
                .param("priority", "HIGH").param("dueDate", LocalDate.now().plusDays(14).toString())
                .with(csrf()).with(user(user(pm))));
        pending = requirements.findForProject(project.getId()).getFirst();
    }

    MockHttpServletRequestBuilder decision(String decision, String reason) {
        return post("/requirements/{id}/decision", pending.getId())
                .param("decision", decision).param("rejectReason", reason)
                .param("version", String.valueOf(pending.getVersion()))
                .with(csrf());
    }

    @Test
    void saleDashboardListsPendingRequirementAndDetailHasReviewButton() throws Exception {
        String review = "/requirements/" + pending.getId() + "/decision";
        mvc.perform(get("/dashboard").with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString("Requirements waiting for my approval")))
                .andExpect(content().string(Matchers.containsString("Add guest Wi-Fi")))
                .andExpect(content().string(Matchers.containsString(review)));
        mvc.perform(get("/dashboard").with(user(user(otherSale))))
                .andExpect(content().string(Matchers.containsString("No requirement is waiting for your approval")));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString("Review Requirement")))
                .andExpect(content().string(Matchers.containsString(review)));
        for (Employee e : new Employee[] {otherSale, presale, pm, anna}) {
            mvc.perform(get("/projects/{id}", project.getId()).with(user(user(e))))
                    .andExpect(model().attribute("toDecide", Matchers.nullValue()))
                    .andExpect(content().string(Matchers.not(Matchers.containsString(review))));
        }
    }

    @Test
    void reviewPageShowsDetailAndAttachments() throws Exception {
        ProjectDocument doc = documents.findAttachments(project.getId()).getFirst();
        mvc.perform(get("/requirements/{id}/decision", pending.getId()).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Customer wants a separate guest SSID")))
                .andExpect(content().string(Matchers.containsString("Max Pm")))
                .andExpect(content().string(Matchers.containsString("Anna Tech")))
                .andExpect(content().string(Matchers.containsString("/documents/" + doc.getId())))
                .andExpect(content().string(Matchers.containsString("layout.pdf")))
                .andExpect(content().string(Matchers.containsString("name=\"version\"")));
    }

    @Test
    void approveThenAttachmentIsPublicOnUc03() throws Exception {
        mvc.perform(decision("APPROVED", "").with(user(user(sale))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("was approved")));
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.NEW_PROJECT);

        ProjectDocument doc = documents.findAttachments(project.getId()).getFirst();
        mvc.perform(get("/documents/{id}", doc.getId()).with(user(user(anna)))).andExpect(status().isOk());
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("Approved")))
                .andExpect(content().string(Matchers.containsString("Sam Sale, ")))   // decided by
                .andExpect(content().string(Matchers.containsString("Requirement Attachment")))
                .andExpect(content().string(Matchers.containsString("/documents/" + doc.getId())));
        // The Presale in charge now has the project to update again (UC02)
        mvc.perform(get("/dashboard").with(user(user(presale))))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/solution")));
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("Approved")));
    }

    @Test
    void rejectWithReasonFinishesProject() throws Exception {
        mvc.perform(decision("REJECTED", "Over budget").with(user(user(sale))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("success", Matchers.containsString("was rejected")));
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.FINISH);

        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(anna))))
                .andExpect(content().string(Matchers.containsString("Reject reason: Over budget")))
                .andExpect(content().string(Matchers.containsString("Finished")))
                // A rejected requirement is never approved, so its files are not "shown after approval"
                .andExpect(content().string(Matchers.containsString("1 file(s), not shared (requirement rejected)")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("shown after approval"))));
        mvc.perform(get("/dashboard").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("Reason: Over budget")));
    }

    @Test
    void missingDecisionOrReasonShowsErrors() throws Exception {
        mvc.perform(post("/requirements/{id}/decision", pending.getId())
                        .param("version", String.valueOf(pending.getVersion()))
                        .with(csrf()).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "decision"))
                .andExpect(content().string(Matchers.containsString("Please choose Approve or Reject")));
        mvc.perform(decision("REJECTED", "   ").with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "rejectReason"))
                .andExpect(content().string(Matchers.containsString("Please give the reason for rejecting")));
        mvc.perform(decision("MAYBE", "").with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Please choose Approve or Reject")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
        mvc.perform(decision("REJECTED", "x".repeat(1001)).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("At most 1000 characters")));
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
    }

    @Test
    void secondDecisionGoesToDashboardWithMessage() throws Exception {
        mvc.perform(decision("APPROVED", "").with(user(user(sale))));
        mvc.perform(decision("REJECTED", "changed my mind").with(user(user(sale))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("already approved")));
        mvc.perform(get("/requirements/{id}/decision", pending.getId()).with(user(user(sale))))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.APPROVED);
    }

    @Test
    void otherPeopleCannotOpenOrDecide() throws Exception {
        for (Employee e : new Employee[] {otherSale, presale, pm, anna}) {
            mvc.perform(get("/requirements/{id}/decision", pending.getId()).with(user(user(e))))
                    .andExpect(status().isForbidden());
            mvc.perform(decision("APPROVED", "").with(user(user(e))))
                    .andExpect(status().isForbidden());
        }
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
    }

    @Test
    void unknownRequirementIs404AndCsrfIsRequired() throws Exception {
        mvc.perform(get("/requirements/{id}/decision", 999_999).with(user(user(sale))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/requirements/{id}/decision", pending.getId())
                        .param("decision", "APPROVED").param("version", "0").with(user(user(sale))))
                .andExpect(status().isForbidden());
        assertThat(requirements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(RequirementStatus.PENDING);
    }
}
