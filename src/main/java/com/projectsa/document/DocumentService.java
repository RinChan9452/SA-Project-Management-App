package com.projectsa.document;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.project.Project;
import com.projectsa.requirement.Requirement;
import com.projectsa.requirement.RequirementStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final ProjectDocumentRepository documents;
    private final FileStorage storage;
    private final EmployeeRepository employees;

    public DocumentService(ProjectDocumentRepository documents, FileStorage storage, EmployeeRepository employees) {
        this.documents = documents;
        this.storage = storage;
        this.employees = employees;
    }

    /** A profile picture and its type. */
    public record Avatar(FileKind kind, Resource resource) {
    }

    /**
     * F4: an employee's profile picture, for any logged-in employee (shown in the navbar, profile page and UC04 list).
     * The path comes from the database, never from the URL.
     *
     * @throws NotFoundException if the employee does not exist or has no picture
     */
    @Transactional(readOnly = true)
    public Avatar avatar(Long employeeId) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        String path = employee.getProfilePicturePath();
        if (path == null) {
            throw new NotFoundException("No profile picture");
        }
        return new Avatar(path.endsWith(FileKind.PNG.getExtension()) ? FileKind.PNG : FileKind.JPG, storage.load(path));
    }

    /** UC03 "Documents": Solution / Product Requirement PDFs and attachments of approved requirements. */
    @Transactional(readOnly = true)
    public List<ProjectDocument> publicDocuments(Long projectId) {
        return documents.findPublicDocuments(projectId);
    }

    /** Every requirement attachment of the project, grouped by requirement ID. */
    @Transactional(readOnly = true)
    public Map<Long, List<ProjectDocument>> attachmentsByRequirement(Long projectId) {
        Map<Long, List<ProjectDocument>> byRequirement = new LinkedHashMap<>();
        for (ProjectDocument d : documents.findAttachments(projectId)) {
            byRequirement.computeIfAbsent(d.getRequirement().getId(), id -> new ArrayList<>()).add(d);
        }
        return byRequirement;
    }

    /** A file to download plus its metadata. */
    public record Download(ProjectDocument document, Resource resource) {
    }

    /**
     * UC03 step 3: any logged-in employee may download Solution / Product Requirement PDFs and the attachments of
     * approved requirements. Attachments of a requirement that is not approved: only the project's Sale in charge
     * (who decides in UC07) and PM in charge (who added it in UC06).
     */
    @Transactional(readOnly = true)
    public Download forDownload(Long documentId, CurrentUser user) {
        if (user == null) {
            throw new AccessDeniedException("Please log in");
        }
        ProjectDocument document = documents.findForDownload(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (document.getRequirement() != null
                && !mayDownloadAttachments(document.getRequirement(), document.getProject(), user)) {
            throw new AccessDeniedException("This document is not available");
        }
        return new Download(document, storage.load(document.getFilePath()));
    }

    /** True when the user may download the attachments of this requirement (same rule as {@link #forDownload}). */
    public static boolean mayDownloadAttachments(Requirement requirement, Project project, CurrentUser user) {
        if (requirement.getStatus() == RequirementStatus.APPROVED) {
            return true;
        }
        return project.getSale().getId().equals(user.getId())
                || (project.getPm() != null && project.getPm().getId().equals(user.getId()));
    }
}
