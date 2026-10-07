package com.projectsa.project;

import com.projectsa.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/**
 * UC04: a Tech Engineer assigned to a project. Primary key (project_id, employee_id), so the same engineer
 * can never be on the same project twice. {@link Persistable} makes {@code save()} always INSERT, so a
 * duplicate fails instead of being merged silently.
 */
@Entity
@Table(name = "project_engineer")
public class ProjectEngineer implements Persistable<ProjectEngineer.Key> {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "project_id")
        private Long projectId;

        @Column(name = "employee_id")
        private Long employeeId;

        protected Key() {
        }

        public Key(Long projectId, Long employeeId) {
            this.projectId = projectId;
            this.employeeId = employeeId;
        }

        public Long getProjectId() {
            return projectId;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(projectId, k.projectId) && Objects.equals(employeeId, k.employeeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, employeeId);
        }
    }

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", insertable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by")
    private Employee assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Transient
    private boolean isNew = true;

    protected ProjectEngineer() {
    }

    public ProjectEngineer(Project project, Employee engineer, Employee assignedBy) {
        this.id = new Key(project.getId(), engineer.getId());
        this.project = project;
        this.employee = engineer;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public Key getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Project getProject() {
        return project;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Employee getAssignedBy() {
        return assignedBy;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
}
