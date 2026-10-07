package com.projectsa.solution;

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
import com.projectsa.project.Project;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** UC02 Store Project Solution Detail — only the project's Presale in charge, only in NEW_PROJECT. */
@Service
public class SolutionService {

    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;
    private final EmployeeRepository employees;
    private final FileStorage storage;

    public SolutionService(ProjectRepository projects, ProjectDocumentRepository documents,
                           EmployeeRepository employees, FileStorage storage) {
        this.projects = projects;
        this.documents = documents;
        this.employees = employees;
        this.storage = storage;
    }

    /** Projects waiting for my solution. */
    @Transactional(readOnly = true)
    public List<Project> waitingForSolution(CurrentUser user) {
        return projects.findByPresaleIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> p.getStatus() == ProjectStatus.NEW_PROJECT)
                .toList();
    }

    /** Loads the project for the UC02 form after checking role, ownership and state. */
    @Transactional(readOnly = true)
    public Project projectForSolution(Long projectId, CurrentUser user) {
        Project project = projects.findDetailById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        checkAllowed(project, user);
        return project;
    }

    @Transactional(readOnly = true)
    public boolean hasDocument(Long projectId, DocumentType type) {
        return documents.existsByProjectIdAndType(projectId, type);
    }

    /**
     * Saves solution details + PDFs and moves the project to WAITING_FOR_ASSIGN_ENGINEER.
     * Both PDFs are required the first time; when coming back after an approved requirement they are
     * optional, and a new upload is added as a new version (old files are kept).
     */
    @Transactional
    public Project save(Long projectId, SolutionForm form, CurrentUser user) {
        Project project = projectForSolution(projectId, user);
        CheckedFiles files = check(projectId, form);

        Employee uploader = employees.getReferenceById(user.getId());
        List<String> saved = new ArrayList<>();
        try {
            store(project, form.getSolutionFile(), files.solution(), DocumentType.SOLUTION, uploader, saved);
            store(project, form.getProductRequirementFile(), files.productRequirement(),
                    DocumentType.PRODUCT_REQUIREMENT, uploader, saved);
            project.setSolution(form.getStartDate(), form.getEndDate(), form.getObjective().trim(), form.getEstBudget());
            project.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
            return projects.saveAndFlush(project);
        } catch (RuntimeException e) {
            saved.forEach(storage::deleteQuietly);   // don't leave orphan files on disk
            throw e;
        }
    }

    /**
     * UC02 business rules only (dates, PDFs), so the form can show them together with its own field errors.
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validate(Long projectId, SolutionForm form, CurrentUser user) {
        projectForSolution(projectId, user);
        check(projectId, form);
    }

    /** Detected type of each upload; null means "no new file, keep the previous version". */
    private record CheckedFiles(FileKind solution, FileKind productRequirement) {
    }

    /** Checks the date order and both PDFs, and reports every problem at once. */
    private CheckedFiles check(Long projectId, SolutionForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getStartDate() != null && form.getEndDate() != null
                && form.getEndDate().isBefore(form.getStartDate())) {
            errors.add(new FieldMessage("endDate", "End date cannot be before the start date"));
        }
        FileKind solution = FieldValidationException.collect(errors,
                () -> checkPdf(form.getSolutionFile(), projectId, DocumentType.SOLUTION, "solutionFile"));
        FileKind productRequirement = FieldValidationException.collect(errors,
                () -> checkPdf(form.getProductRequirementFile(), projectId, DocumentType.PRODUCT_REQUIREMENT,
                        "productRequirementFile"));
        FieldValidationException.throwIfAny(errors);
        return new CheckedFiles(solution, productRequirement);
    }

    /** Whether the user may do UC02 on this project now (used to show the Add Solution buttons). */
    public static boolean canStoreSolution(Project project, CurrentUser user) {
        return user.getRole() == Role.PRESALE
                && project.getPresale().getId().equals(user.getId())
                && project.getStatus() == ProjectStatus.NEW_PROJECT;
    }

    private void checkAllowed(Project project, CurrentUser user) {
        if (user == null || user.getRole() != Role.PRESALE) {
            throw new AccessDeniedException("Only a Presale Engineer can store a solution");
        }
        if (!project.getPresale().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the Presale Engineer in charge of this project can store its solution");
        }
        if (project.getStatus() != ProjectStatus.NEW_PROJECT) {
            throw new ActionNotAllowedException("The solution can only be stored while the project is \""
                    + ProjectStatus.NEW_PROJECT.getLabel() + "\". This project is \"" + project.getStatus().getLabel() + "\".");
        }
    }

    /** Returns null when the file may be skipped (a previous version exists). */
    private FileKind checkPdf(MultipartFile file, Long projectId, DocumentType type, String field) {
        if ((file == null || file.isEmpty()) && documents.existsByProjectIdAndType(projectId, type)) {
            return null;
        }
        return storage.check(file, Set.of(FileKind.PDF), FileStorage.MAX_DOCUMENT_BYTES, field);
    }

    private void store(Project project, MultipartFile file, FileKind kind, DocumentType type,
                       Employee uploader, List<String> saved) {
        if (kind == null) {
            return;
        }
        String path = storage.save(file, kind, "projects/" + project.getId());
        saved.add(path);
        documents.save(new ProjectDocument(project, type, FileStorage.originalName(file, kind), path, kind.getMime(),
                file.getSize(), uploader));
    }
}
