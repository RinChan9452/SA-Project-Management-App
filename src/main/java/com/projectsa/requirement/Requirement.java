package com.projectsa.requirement;

import com.projectsa.employee.Employee;
import com.projectsa.project.Project;
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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * UC06: a new customer requirement the PM in charge adds after the customer test. Starts PENDING; the Sale in
 * charge approves or rejects it in UC07 ({@code @Version} prevents two decisions on the same requirement).
 */
@Entity
@Table(name = "requirement")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "related_feature", length = 200)
    private String relatedFeature;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RequirementStatus status;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Employee decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Version
    private long version;

    protected Requirement() {
    }

    /** UC06: a new requirement is always PENDING. */
    public Requirement(Project project, String title, String detail, Priority priority, LocalDate dueDate,
                       String relatedFeature, Employee createdBy) {
        this.project = project;
        this.title = title;
        this.detail = detail;
        this.priority = priority;
        this.dueDate = dueDate;
        this.relatedFeature = relatedFeature;
        this.createdBy = createdBy;
        this.status = RequirementStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * UC07: the Sale in charge approves or rejects a PENDING requirement. Only a rejection keeps a reason.
     *
     * @throws IllegalStateException if the requirement was already decided or {@code decision} is PENDING
     */
    public void decide(RequirementStatus decision, String rejectReason, Employee decidedBy) {
        if (status != RequirementStatus.PENDING || decision == RequirementStatus.PENDING) {
            throw new IllegalStateException("Requirement " + id + " cannot be decided again");
        }
        this.status = decision;
        this.rejectReason = decision == RequirementStatus.REJECTED ? rejectReason : null;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getRelatedFeature() {
        return relatedFeature;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Employee getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public long getVersion() {
        return version;
    }
}
