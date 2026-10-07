package com.projectsa.requirement;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectTesting;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.tech;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.document.DocumentType;
import com.projectsa.document.FileStorage;
import com.projectsa.document.ProjectDocument;
import com.projectsa.document.ProjectDocumentRepository;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationRepository;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import com.projectsa.testschedule.TestScheduleRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** UC06 Add Customer New Requirement. */
@SpringBootTest
@Transactional
class RequirementServiceTest {

    static final byte[] PDF = "%PDF-1.4\nchange request".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    @Autowired
    RequirementService service;
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
    EmployeeRepository employees;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    FileStorage storage;

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
                LocalDateTime.now().minusHours(2), sale, presale, pm, anna);
    }

    static RequirementForm form(LocalDate dueDate, MultipartFile... attachments) {
        RequirementForm f = new RequirementForm();
        f.setTitle("  Add guest Wi-Fi  ");
        f.setDetail("  Customer wants a separate guest SSID  ");
        f.setPriority(Priority.HIGH);
        f.setDueDate(dueDate);
        f.setRelatedFeature("  ");
        f.setAttachments(List.of(attachments));
        return f;
    }

    static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("attachments", name, "application/octet-stream", content);
    }

    @Test
    void pmInChargeAddsRequirementProjectWaitsForApprovalAndSaleIsNotified() {
        Requirement saved = service.save(project.getId(),
                form(LocalDate.now(), file("C:\\scan\\layout.pdf", PDF), file("photo.png", PNG), file("site.jpg", JPG)),
                user(pm));

        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.WAITING_FOR_APPROVE);
        assertThat(service.requirementsOf(project.getId())).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(saved.getId());
            assertThat(r.getTitle()).isEqualTo("Add guest Wi-Fi");
            assertThat(r.getDetail()).isEqualTo("Customer wants a separate guest SSID");
            assertThat(r.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(r.getDueDate()).isEqualTo(LocalDate.now());
            assertThat(r.getRelatedFeature()).isNull();
            assertThat(r.getStatus()).isEqualTo(RequirementStatus.PENDING);
            assertThat(r.getCreatedBy().getId()).isEqualTo(pm.getId());
            assertThat(r.getCreatedAt()).isNotNull();
            assertThat(r.getDecidedBy()).isNull();
            assertThat(r.getDecidedAt()).isNull();
        });

        List<ProjectDocument> files = documents.findAttachments(project.getId());
        assertThat(files).extracting(ProjectDocument::getOriginalName, ProjectDocument::getMime)
                .containsExactly(tuple("layout.pdf", "application/pdf"),
                        tuple("photo.png", "image/png"),
                        tuple("site.jpg", "image/jpeg"));
        assertThat(files).allSatisfy(d -> {
            assertThat(d.getType()).isEqualTo(DocumentType.REQUIREMENT_ATTACHMENT);
            assertThat(d.getRequirement().getId()).isEqualTo(saved.getId());
            assertThat(d.getFilePath()).startsWith("projects/" + project.getId() + "/").doesNotContain("layout");
            assertThat(storage.load(d.getFilePath()).exists()).isTrue();
        });
        // Not visible as a public document until the Sale approves it
        assertThat(documents.findPublicDocuments(project.getId())).isEmpty();

        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getRecipient().getId()).isEqualTo(sale.getId());
            assertThat(n.getType()).isEqualTo(NotificationType.REQUIREMENT_PENDING);
            assertThat(n.getProject().getId()).isEqualTo(project.getId());
            assertThat(n.getMessage()).contains("Add guest Wi-Fi", "Network Upgrade", "Max Pm");
            assertThat(n.getSendAt()).isBeforeOrEqualTo(LocalDateTime.now());
        });
    }

    @Test
    void attachmentsAreOptionalAndAnEmptyFileInputIsIgnored() {
        RequirementForm f = form(LocalDate.now().plusDays(7), file("", new byte[0]));
        f.setRelatedFeature(" Wireless ");
        service.save(project.getId(), f, user(pm));

        assertThat(requirements.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRelatedFeature()).isEqualTo("Wireless"));
        assertThat(documents.findAttachments(project.getId())).isEmpty();
    }

    @Test
    void onlyProjectManagers() {
        for (Employee e : List.of(sale, presale, anna)) {
            assertThatThrownBy(() -> service.save(project.getId(), form(LocalDate.now()), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.projectForRequirement(project.getId(), user(e)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(requirements.count()).isZero();
    }

    @Test
    void onlyThePmInCharge() {
        assertThatThrownBy(() -> service.save(project.getId(), form(LocalDate.now()), user(otherPm)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("PM in charge");
        assertThat(requirements.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void onlyAfterTheCustomerTest() {
        Project upcoming = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().plusDays(1), sale, presale, pm, anna);
        assertThatThrownBy(() -> service.save(upcoming.getId(), form(LocalDate.now()), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("after the customer test");
        assertThatThrownBy(() -> service.projectForRequirement(upcoming.getId(), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class);
        assertThat(projects.findById(upcoming.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);

        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 9, 0);
        assertThat(RequirementService.isTestFinished(now, now)).isTrue();
        assertThat(RequirementService.isTestFinished(now.plusMinutes(1), now)).isFalse();
        assertThat(RequirementService.isTestFinished(null, now)).isFalse();
    }

    @Test
    void onlyWhileTesting() {
        Project working = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, anna);
        assertThatThrownBy(() -> service.save(working.getId(), form(LocalDate.now()), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("Working");

        service.save(project.getId(), form(LocalDate.now()), user(pm));
        assertThatThrownBy(() -> service.save(project.getId(), form(LocalDate.now()), user(pm)))
                .isInstanceOf(ActionNotAllowedException.class).hasMessageContaining("Waiting for Approval");
        assertThat(requirements.count()).isEqualTo(1);
    }

    @Test
    void dueDateCannotBeInThePast() {
        assertThatThrownBy(() -> service.save(project.getId(), form(LocalDate.now().minusDays(1)), user(pm)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "dueDate")
                .hasMessageContaining("past");
        assertThat(requirements.count()).isZero();
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.TESTING);
    }

    @Test
    void attachmentsMustReallyBePdfJpgOrPngAndEveryBadFileIsListed() {
        RequirementForm f = form(LocalDate.now().minusDays(1), file("notes.txt", "hello".getBytes()),
                file("fake.pdf", "not a pdf".getBytes()), file("ok.pdf", PDF));
        FieldValidationException e = catchThrowableOfType(FieldValidationException.class,
                () -> service.save(project.getId(), f, user(pm)));

        assertThat(e.getErrors()).extracting(FieldValidationException.FieldMessage::field)
                .containsExactly("dueDate", "attachments");
        assertThat(e.getErrors().get(1).message())
                .contains("notes.txt", "fake.pdf", "Only PDF, PNG, JPG files are allowed")
                .doesNotContain("ok.pdf");
        assertThat(requirements.count()).isZero();
        assertThat(documents.count()).isZero();
    }

    @Test
    void attachmentLargerThan50MbIsRejected() {
        MockMultipartFile huge = new MockMultipartFile("attachments", "huge.pdf", "application/pdf", PDF) {
            @Override
            public long getSize() {
                return FileStorage.MAX_DOCUMENT_BYTES + 1;
            }
        };
        assertThatThrownBy(() -> service.validate(project.getId(), form(LocalDate.now(), huge), user(pm)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "attachments")
                .hasMessageContaining("huge.pdf").hasMessageContaining("50 MB");
    }

    @Test
    void pmDashboardListsTestingProjectsAndMyRequirements() {
        Project upcoming = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().plusDays(1), sale, presale, pm, anna);
        assertThat(service.myTestingProjects(user(pm)))
                .extracting(t -> t.project().getId(), RequirementService.TestingProject::testFinished)
                .containsExactlyInAnyOrder(tuple(project.getId(), true),
                        tuple(upcoming.getId(), false));
        assertThat(service.myTestingProjects(user(otherPm))).isEmpty();

        service.save(project.getId(), form(LocalDate.now()), user(pm));
        assertThat(service.myTestingProjects(user(pm))).extracting(t -> t.project().getId())
                .containsExactly(upcoming.getId());
        assertThat(service.createdBy(user(pm))).singleElement().satisfies(r -> {
            assertThat(r.getProject().getProjectName()).isEqualTo("Network Upgrade");
            assertThat(r.getStatus()).isEqualTo(RequirementStatus.PENDING);
        });
        assertThat(service.createdBy(user(otherPm))).isEmpty();
    }
}
