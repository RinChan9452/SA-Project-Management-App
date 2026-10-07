package com.projectsa.solution;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectForm;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.document.DocumentType;
import com.projectsa.document.ProjectDocument;
import com.projectsa.document.ProjectDocumentRepository;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** UC02 Store Project Solution Detail. */
@SpringBootTest
@Transactional
class SolutionServiceTest {

    static final byte[] PDF = "%PDF-1.4\nsolution".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    SolutionService service;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectDocumentRepository documents;
    @Autowired
    EmployeeRepository employees;

    Employee sale;
    Employee presale;
    Employee otherPresale;
    Employee pm;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        otherPresale = employee(employees, "Pia Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        project = projectService.create(projectForm(sale.getId(), presale.getId()), user(sale));
    }

    static SolutionForm form(boolean withFiles) {
        SolutionForm f = new SolutionForm();
        f.setStartDate(LocalDate.of(2026, 11, 1));
        f.setEndDate(LocalDate.of(2026, 12, 31));
        f.setObjective("  Replace core switches  ");
        f.setEstBudget(new BigDecimal("150000.50"));
        if (withFiles) {
            f.setSolutionFile(new MockMultipartFile("solutionFile", "solution.pdf", "application/pdf", PDF));
            f.setProductRequirementFile(new MockMultipartFile("productRequirementFile", "C:\\Users\\me\\req.pdf", "application/pdf", PDF));
        }
        return f;
    }

    @Test
    void presaleInChargeStoresSolutionAndProjectMovesOn() {
        service.save(project.getId(), form(true), user(presale));

        Project saved = projects.findById(project.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        assertThat(saved.getObjective()).isEqualTo("Replace core switches");
        assertThat(saved.getEstBudget()).isEqualByComparingTo("150000.50");
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));

        assertThat(documents.findSolutionDocuments(project.getId()))
                .extracting(ProjectDocument::getType, ProjectDocument::getOriginalName, ProjectDocument::getMime)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(DocumentType.SOLUTION, "solution.pdf", "application/pdf"),
                        org.assertj.core.groups.Tuple.tuple(DocumentType.PRODUCT_REQUIREMENT, "req.pdf", "application/pdf"));
    }

    @Test
    void onlyThePresaleInCharge() {
        assertThatThrownBy(() -> service.save(project.getId(), form(true), user(otherPresale)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("in charge");
        assertThatThrownBy(() -> service.save(project.getId(), form(true), user(sale)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.save(project.getId(), form(true), user(pm)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.NEW_PROJECT);
    }

    @Test
    void onlyInNewProjectState() {
        service.save(project.getId(), form(true), user(presale));
        assertThatThrownBy(() -> service.save(project.getId(), form(true), user(presale)))
                .isInstanceOf(ActionNotAllowedException.class);
    }

    @Test
    void endDateCannotBeBeforeStartDate() {
        SolutionForm f = form(true);
        f.setEndDate(LocalDate.of(2026, 10, 1));
        assertThatThrownBy(() -> service.save(project.getId(), f, user(presale)))
                .isInstanceOf(FieldValidationException.class).hasFieldOrPropertyWithValue("field", "endDate");
    }

    @Test
    void bothPdfsRequiredTheFirstTime() {
        SolutionForm f = form(true);
        f.setProductRequirementFile(null);
        assertThatThrownBy(() -> service.save(project.getId(), f, user(presale)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "productRequirementFile");
        assertThat(documents.count()).isZero();
    }

    @Test
    void allProblemsReportedAtOnce() {
        SolutionForm f = form(false);
        f.setEndDate(LocalDate.of(2026, 10, 1));
        f.setSolutionFile(new MockMultipartFile("solutionFile", "solution.pdf", "application/pdf", "not a pdf".getBytes()));
        assertThatThrownBy(() -> service.save(project.getId(), f, user(presale)))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldValidationException.FieldMessage::field)
                        .containsExactly("endDate", "solutionFile", "productRequirementFile"));
        assertThat(documents.count()).isZero();
    }

    @Test
    void nonPdfContentRejected() {
        SolutionForm f = form(true);
        f.setSolutionFile(new MockMultipartFile("solutionFile", "solution.pdf", "application/pdf", "not a pdf".getBytes()));
        assertThatThrownBy(() -> service.save(project.getId(), f, user(presale)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "solutionFile");
    }

    @Test
    void comingBackLaterFilesAreOptionalAndNewVersionsKeepOldOnes() {
        service.save(project.getId(), form(true), user(presale));
        // Simulate UC07 approve: project goes back to NEW_PROJECT
        Project p = projects.findById(project.getId()).orElseThrow();
        p.changeStatus(ProjectStatus.WORKING);
        p.changeStatus(ProjectStatus.TESTING);
        p.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        p.changeStatus(ProjectStatus.NEW_PROJECT);
        projects.saveAndFlush(p);

        SolutionForm noFiles = form(false);
        noFiles.setSolutionFile(new MockMultipartFile("solutionFile", "solution-v2.pdf", "application/pdf", PDF));
        service.save(project.getId(), noFiles, user(presale));

        assertThat(documents.findSolutionDocuments(project.getId())).hasSize(3)
                .first().extracting(ProjectDocument::getOriginalName).isEqualTo("solution-v2.pdf");
    }
}
