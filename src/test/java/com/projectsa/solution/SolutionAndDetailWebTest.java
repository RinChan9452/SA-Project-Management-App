package com.projectsa.solution;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectForm;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.document.ProjectDocument;
import com.projectsa.document.ProjectDocumentRepository;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** UC02 + UC03 over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SolutionAndDetailWebTest {

    static final byte[] PDF = "%PDF-1.4\nsolution".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectDocumentRepository documents;

    Employee sale;
    Employee presale;
    Employee otherPresale;
    Employee tech;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        otherPresale = employee(employees, "Pia Presale", Role.PRESALE);
        tech = employee(employees, "Tia Tech", Role.TECH);
        project = projectService.create(projectForm(sale.getId(), presale.getId()), user(sale));
    }

    MockMultipartHttpServletRequestBuilder solutionPost() {
        return (MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/solution", project.getId())
                .file(new MockMultipartFile("solutionFile", "solution.pdf", "application/pdf", PDF))
                .file(new MockMultipartFile("productRequirementFile", "requirement.pdf", "application/pdf", PDF))
                .param("startDate", "2026-11-01")
                .param("endDate", "2026-12-31")
                .param("objective", "Replace core switches")
                .param("estBudget", "150000")
                .with(csrf());
    }

    @Test
    void presaleInChargeSeesButtonOnDetailAndDashboard() throws Exception {
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(presale))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canStoreSolution", true));
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(otherPresale))))
                .andExpect(model().attribute("canStoreSolution", false));
        mvc.perform(get("/dashboard").with(user(user(presale))))
                .andExpect(content().string(Matchers.containsString("Need solution")))
                .andExpect(content().string(Matchers.containsString("/projects/" + project.getId() + "/solution")));
    }

    @Test
    void storeSolutionThenEveryoneCanSeeAndDownload() throws Exception {
        mvc.perform(solutionPost().with(user(user(presale))))
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attributeExists("success"));
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);

        // UC03: any role sees the detail page with the solution and documents
        mvc.perform(get("/projects/{id}", project.getId()).with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Replace core switches")))
                .andExpect(content().string(Matchers.containsString("Pat Presale")))
                .andExpect(content().string(Matchers.containsString("solution.pdf")))
                .andExpect(content().string(Matchers.containsString("Waiting for Engineer Assignment")));

        ProjectDocument doc = documents.findSolutionDocuments(project.getId()).getFirst();
        mvc.perform(get("/documents/{id}", doc.getId()).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void otherPresaleCannotOpenOrPostTheForm() throws Exception {
        mvc.perform(get("/projects/{id}/solution", project.getId()).with(user(user(otherPresale))))
                .andExpect(status().isForbidden());
        mvc.perform(solutionPost().with(user(user(otherPresale)))).andExpect(status().isForbidden());
        mvc.perform(solutionPost().with(user(user(sale)))).andExpect(status().isForbidden());
        assertThat(documents.count()).isZero();
    }

    @Test
    void wrongStateShowsMessageOnDashboard() throws Exception {
        mvc.perform(solutionPost().with(user(user(presale)))).andExpect(redirectedUrl("/projects/" + project.getId()));
        mvc.perform(get("/projects/{id}/solution", project.getId()).with(user(user(presale))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", Matchers.containsString("New Project")));
    }

    @Test
    void validationErrorsShowOnForm() throws Exception {
        mvc.perform(((MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/solution", project.getId()))
                        .param("startDate", "2026-12-31").param("endDate", "2026-11-01")
                        .param("objective", "x").param("estBudget", "-5")
                        .with(csrf()).with(user(user(presale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "estBudget"));
        mvc.perform(((MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/solution", project.getId()))
                        .param("startDate", "2026-12-31").param("endDate", "2026-11-01")
                        .param("objective", "x").param("estBudget", "5")
                        .with(csrf()).with(user(user(presale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "endDate"));
    }

    @Test
    void emptySubmitShowsEveryErrorIncludingFiles() throws Exception {
        mvc.perform(((MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/solution", project.getId()))
                        .with(csrf()).with(user(user(presale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "startDate", "endDate", "objective", "estBudget",
                        "solutionFile", "productRequirementFile"));
        assertThat(documents.count()).isZero();
    }

    @Test
    void budgetThatIsNotANumberShowsPlainMessage() throws Exception {
        mvc.perform(((MockMultipartHttpServletRequestBuilder) multipart("/projects/{id}/solution", project.getId()))
                        .param("startDate", "2026-11-01").param("endDate", "2026-12-31")
                        .param("objective", "x").param("estBudget", "abc")
                        .with(csrf()).with(user(user(presale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "estBudget"))
                .andExpect(content().string(Matchers.containsString("Estimated budget must be a number")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
    }

    @Test
    void unknownProjectAndDocumentGive404AndDownloadNeedsLogin() throws Exception {
        mvc.perform(get("/projects/999999").with(user(user(tech)))).andExpect(status().isNotFound());
        mvc.perform(get("/documents/999999").with(user(user(tech)))).andExpect(status().isNotFound());
        mvc.perform(get("/documents/1")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void projectListShowsAllProjectsToEveryRole() throws Exception {
        mvc.perform(get("/projects").with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Network Upgrade")))
                .andExpect(content().string(Matchers.containsString("Sam Sale")));
    }
}
