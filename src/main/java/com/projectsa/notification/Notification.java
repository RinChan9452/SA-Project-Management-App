package com.projectsa.notification;

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
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An in-app message for one employee. It shows from {@code sendAt} on, until it is read. */
@Entity
@Table(name = "notification")
public class Notification {

    /** Size of the {@code message} column; longer messages are cut so saving never fails. */
    static final int MAX_MESSAGE = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Employee recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = MAX_MESSAGE)
    private String message;

    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Employee recipient, Project project, NotificationType type, String message,
                        LocalDateTime sendAt) {
        this.recipient = recipient;
        this.project = project;
        this.type = type;
        this.message = message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
        this.sendAt = sendAt;
        this.createdAt = LocalDateTime.now();
    }

    /** F2: marks it read (keeps the first read time if it was read before). */
    void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public Employee getRecipient() {
        return recipient;
    }

    public Project getProject() {
        return project;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getSendAt() {
        return sendAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
