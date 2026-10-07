package com.projectsa.notification;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.project.Project;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates in-app notifications (in-app only, never email) and lets each employee read their own (F2).
 * A notification shows from its {@code sendAt} on, so a UC05 reminder appears by itself 1 day before the test.
 */
@Service
public class NotificationService {

    /** F2 list page size. */
    public static final int PAGE_SIZE = 20;
    /** How many unread notifications the dashboard shows (F1). */
    public static final int DASHBOARD_LATEST = 5;

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Sends now. Must run inside the caller's transaction, so it is rolled back together with the action. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notification notify(Employee recipient, Project project, NotificationType type, String message) {
        return notifications.save(new Notification(recipient, project, type, message, LocalDateTime.now()));
    }

    /** Shows from {@code sendAt} on (e.g. a reminder). Same transaction rule as {@link #notify}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notification notifyAt(Employee recipient, Project project, NotificationType type, String message,
                                 LocalDateTime sendAt) {
        return notifications.save(new Notification(recipient, project, type, message, sendAt));
    }

    /** Bell badge: my unread notifications that are already sent. */
    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return notifications.countByRecipientIdAndReadAtIsNullAndSendAtLessThanEqual(user.getId(), LocalDateTime.now());
    }

    /** F2 list: my sent notifications, newest first. {@code page} starts at 1; a page past the end shows the last page. */
    @Transactional(readOnly = true)
    public Page<Notification> inbox(CurrentUser user, int page) {
        LocalDateTime now = LocalDateTime.now();
        Page<Notification> result = notifications.inbox(user.getId(), now, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            int last = Math.max(result.getTotalPages() - 1, 0);
            result = notifications.inbox(user.getId(), now, PageRequest.of(last, PAGE_SIZE));
        }
        return result;
    }

    /** F1: my latest unread notifications. */
    @Transactional(readOnly = true)
    public List<Notification> latestUnread(CurrentUser user) {
        return notifications.latestUnread(user.getId(), LocalDateTime.now(), Limit.of(DASHBOARD_LATEST));
    }

    /**
     * Opens one of my notifications: marks it read and returns it (the caller shows its project).
     *
     * @throws NotFoundException if it does not exist, belongs to someone else or is not sent yet
     */
    @Transactional
    public Notification open(Long id, CurrentUser user) {
        Notification n = notifications.findMine(id, user.getId(), LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.markRead(LocalDateTime.now());
        return n;
    }

    /** Marks all my sent notifications as read; returns how many were unread. */
    @Transactional
    public int markAllRead(CurrentUser user) {
        return notifications.markAllRead(user.getId(), LocalDateTime.now());
    }
}
