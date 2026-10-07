package com.projectsa.notification;

import com.projectsa.auth.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** F2: puts my unread count ({@code unreadCount}) in every page's model for the navbar bell. */
@ControllerAdvice
public class NotificationBellAdvice {

    private final NotificationService notificationService;

    public NotificationBellAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadCount")
    Long unreadCount(@AuthenticationPrincipal CurrentUser user) {
        if (user == null) {
            return null;
        }
        try {
            return notificationService.unreadCount(user);
        } catch (RuntimeException e) {
            return null;   // never break a page (e.g. the error page itself) just because the badge can't be counted
        }
    }
}
