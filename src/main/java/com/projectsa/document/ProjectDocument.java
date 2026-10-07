package com.projectsa.document;

import com.projectsa.employee.Employee;
import com.projectsa.project.Project;
import com.projectsa.requirement.Requirement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An uploaded file. The bytes are on disk ({@link FileStorage}); this row keeps the metadata. */
@Entity
@Table(name = "project_document")
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    /** Set only for REQUIREMENT_ATTACHMENT (UC06). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    private Requirement requirement;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false, length = 100)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private Employee uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    protected ProjectDocument() {
    }

    public ProjectDocument(Project project, DocumentType type, String originalName, String filePath,
                           String mime, long sizeBytes, Employee uploadedBy) {
        this(project, null, type, originalName, filePath, mime, sizeBytes, uploadedBy);
    }

    /** A file that belongs to a requirement (UC06 attachment). */
    public ProjectDocument(Project project, Requirement requirement, DocumentType type, String originalName,
                           String filePath, String mime, long sizeBytes, Employee uploadedBy) {
        this.project = project;
        this.requirement = requirement;
        this.type = type;
        this.originalName = originalName;
        this.filePath = filePath;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Requirement getRequirement() {
        return requirement;
    }

    public DocumentType getType() {
        return type;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getMime() {
        return mime;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Employee getUploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
