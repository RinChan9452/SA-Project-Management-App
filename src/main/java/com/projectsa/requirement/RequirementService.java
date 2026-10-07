package com.projectsa.requirement;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.common.NotFoundException;
import com.projectsa.document.DocumentType;
import com.projectsa.document.FileKind;
import com.projectsa.document.FileStorage;
import com.projectsa.document.ProjectDocument;
import com.projectsa.document.ProjectDocumentRepository;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.CompleteProjectService;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectStatus;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC06 Add Customer New Requirement — only the project's PM in charge, only in TESTING and only after the
 * customer test has taken place (latest test date/time has passed).
 */
@Service
public class RequirementService {

    /** UC06 attachments: PDF, JPG or PNG, checked by content (§8). */
    static final Set<FileKind> ATTACHMENT_KINDS = Set.of(FileKind.PDF, FileKind.JPG, FileKind.PNG);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final ProjectRepository projects;
    private final RequirementRepository requirements;
    private final TestScheduleRepository schedules;
    private final ProjectDocumentRepository documents;
    private final EmployeeRepository employees;
    private final FileStorage storage;
    private final NotificationService notifications;

    public RequirementService(ProjectRepository projects, RequirementRepository requirements,
                              TestScheduleRepository schedules, ProjectDocumentRepository documents,
                              EmployeeRepository employees, FileStorage storage, NotificationService notifications) {
        this.projects = projects;
        this.requirements = requirements;
        this.schedules = schedules;
        this.documents = documents;
        this.employees = employees;
        this.storage = storage;
        this.notifications = notifications;
    }

    /** UC03 and UC06 step 3.7: every requirement of the project, newest first. */
    @Transactional(readOnly = true)
    public List<Requirement> requirementsOf(Long projectId) {
        return requirements.findForProject(projectId);
    }

    /** PM dashboard: requirements I added and their status, newest first. */
    @Transactional(readOnly = true)
    public List<Requirement> createdBy(CurrentUser user) {
        return requirements.findCreatedBy(user.getId());
    }

    /** The latest customer test date/time of the project, or null if none was set. */
    @Transactional(readOnly = true)
    public LocalDateTime latestTestAt(Long projectId) {
        return schedules.findLatestTestAt(projectId);
    }

    /** A project of mine in TESTING with its latest customer test (PM dashboard). */
    public record TestingProject(Project project, LocalDateTime testAt) {
        public boolean testFinished() {
            return isTestFinished(testAt, LocalDateTime.now());
        }

        /** Complete Project is available from here on (one week after the test). */
        public LocalDateTime completeFrom() {
            return CompleteProjectService.completeFrom(testAt);
        }

        public boolean canComplete() {
            return CompleteProjectService.isWindowOver(testAt, LocalDateTime.now());
        }
    }

    /** PM dashboard: my projects in TESTING, so I can add a requirement once the customer test is over. */
    @Transactional(readOnly = true)
    public List<TestingProject> myTestingProjects(CurrentUser user) {
        return projects.findByPmIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> p.getStatus() == ProjectStatus.TESTING)
                .map(p -> new TestingProject(p, schedules.findLatestTestAt(p.getId())))
                .toList();
    }

    /** Loads the project for the UC06 form after checking role, PM in charge, state and test time. */
    @Transactional(readOnly = true)
    public Project projectForRequirement(Long projectId, CurrentUser user) {
        Project project = projects.findDetailById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        checkAllowed(project, user);
        return project;
    }

    /**
     * UC06 steps 4–10: saves the requirement (PENDING) with its attachments, moves the project to
     * WAITING_FOR_APPROVE and notifies the Sale in charge.
     *
     * @throws FieldValidationException  on {@code dueDate} / {@code attachments} if a business rule fails
     * @throws ActionNotAllowedException if the project is not TESTING, the test is not over yet, or someone else
     *                                   changed the project at the same moment
     */
    @Transactional
    public Requirement save(Long projectId, RequirementForm form, CurrentUser user) {
        Project project = projectForRequirement(projectId, user);
        List<CheckedFile> files = check(form);

        Employee pm = employees.getReferenceById(user.getId());
        Requirement requirement = requirements.save(new Requirement(project, form.getTitle().trim(),
                form.getDetail().trim(), form.getPriority(), form.getDueDate(), blankToNull(form.getRelatedFeature()), pm));
        List<String> saved = new ArrayList<>();
        try {
            for (CheckedFile f : files) {
                String path = storage.save(f.file(), f.kind(), "projects/" + project.getId());
                saved.add(path);
                documents.save(new ProjectDocument(project, requirement, DocumentType.REQUIREMENT_ATTACHMENT,
                        FileStorage.originalName(f.file(), f.kind()), path, f.kind().getMime(), f.file().getSize(), pm));
            }
            project.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
            projects.saveAndFlush(project);
            notifications.notify(project.getSale(), project, NotificationType.REQUIREMENT_PENDING,
                    "New customer requirement \"" + requirement.getTitle() + "\" (" + requirement.getPriority().getLabel()
                            + " priority) for project \"" + project.getProjectName() + "\" (ID " + project.getId()
                            + ") from " + user.getName() + " is waiting for your approval.");
        } catch (ObjectOptimisticLockingFailureException e) {
            saved.forEach(storage::deleteQuietly);
            throw new ActionNotAllowedException("This project was just changed by someone else. Please open it again.");
        } catch (RuntimeException e) {
            saved.forEach(storage::deleteQuietly);   // don't leave orphan files on disk
            throw e;
        }
        return requirement;
    }

    /**
     * UC06 business rules only (due date, attachments), so the form can show them together with its own field errors.
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validate(Long projectId, RequirementForm form, CurrentUser user) {
        projectForRequirement(projectId, user);
        check(form);
    }

    /** True when the user may open UC06 for this project (for the button on UC03). */
    public static boolean canAddRequirement(Project project, LocalDateTime latestTestAt, CurrentUser user) {
        return user.getRole() == Role.PM
                && project.getPm() != null && project.getPm().getId().equals(user.getId())
                && project.getStatus() == ProjectStatus.TESTING
                && isTestFinished(latestTestAt, LocalDateTime.now());
    }

    /** The customer test is over once its date/time is reached; no test date means it has not happened. */
    static boolean isTestFinished(LocalDateTime testAt, LocalDateTime now) {
        return testAt != null && !testAt.isAfter(now);
    }

    private record CheckedFile(MultipartFile file, FileKind kind) {
    }

    /** Checks the due date and every attachment, and reports every problem at once. */
    private List<CheckedFile> check(RequirementForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getDueDate() != null && form.getDueDate().isBefore(LocalDate.now())) {
            errors.add(new FieldMessage("dueDate", "Customer due date cannot be in the past"));
        }
        List<CheckedFile> files = new ArrayList<>();
        List<String> badFiles = new ArrayList<>();
        for (MultipartFile file : form.uploadedAttachments()) {
            try {
                files.add(new CheckedFile(file,
                        storage.check(file, ATTACHMENT_KINDS, FileStorage.MAX_DOCUMENT_BYTES, "attachments")));
            } catch (FieldValidationException e) {
                badFiles.add(FileStorage.originalName(file, null) + ": " + e.getMessage());
            }
        }
        if (!badFiles.isEmpty()) {
            // One message for the field, so every bad file is listed (a field shows a single error)
            errors.add(new FieldMessage("attachments", String.join("; ", badFiles)));
        }
        FieldValidationException.throwIfAny(errors);
        return files;
    }

    private void checkAllowed(Project project, CurrentUser user) {
        if (user == null || user.getRole() != Role.PM) {
            throw new AccessDeniedException("Only a Project Manager can add a customer requirement");
        }
        if (project.getPm() == null || !project.getPm().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the PM in charge of this project can add a customer requirement");
        }
        if (project.getStatus() != ProjectStatus.TESTING) {
            throw new ActionNotAllowedException("A new requirement can only be added while the project is \""
                    + ProjectStatus.TESTING.getLabel() + "\". This project is \"" + project.getStatus().getLabel() + "\".");
        }
        LocalDateTime testAt = schedules.findLatestTestAt(project.getId());
        if (!isTestFinished(testAt, LocalDateTime.now())) {
            throw new ActionNotAllowedException("A new requirement can only be added after the customer test"
                    + (testAt == null ? "." : " on " + testAt.format(WHEN) + "."));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
