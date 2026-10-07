package com.projectsa.testschedule;

import com.projectsa.employee.Employee;
import com.projectsa.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** UC05: the customer test date/time a Tech Engineer set for a project. A new row per round, old ones kept. */
@Entity
@Table(name = "project_test_schedule")
public class TestSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "test_at", nullable = false)
    private LocalDateTime testAt;

    @Column(nullable = false, length = 2000)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TestSchedule() {
    }

    public TestSchedule(Project project, LocalDateTime testAt, String detail, Employee createdBy) {
        this.project = project;
        this.testAt = testAt;
        this.detail = detail;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public LocalDateTime getTestAt() {
        return testAt;
    }

    public String getDetail() {
        return detail;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
