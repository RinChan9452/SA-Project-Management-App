package com.projectsa.notification;

import com.projectsa.auth.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F2: my notifications. Every logged-in employee, own notifications only (checked in the service). */
@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    String list(@RequestParam(required = false) String page, @AuthenticationPrincipal CurrentUser user, Model model) {
        Page<Notification> result = notificationService.inbox(user, parsePage(page));
        model.addAttribute("notifications", result);
        model.addAttribute("current", result.getNumber() + 1);
        model.addAttribute("last", Math.max(result.getTotalPages(), 1));
        return "notification/list";
    }

    /** Marks it read and opens its project (UC03); a notification without a project goes back to the list. */
    @PostMapping("/notifications/{id}/open")
    String open(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        Notification n = notificationService.open(id, user);
        return n.getProject() != null ? "redirect:/projects/" + n.getProject().getId() : "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    String markAllRead(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        int marked = notificationService.markAllRead(user);
        redirect.addFlashAttribute("success", marked == 0 ? "You have no unread notifications."
                : marked + (marked == 1 ? " notification" : " notifications") + " marked as read.");
        return "redirect:/notifications";
    }

    /**
     * Lenient like the F3 list: anything that is not a page number shows page 1. Huge numbers are capped so the row
     * offset still fits in an int; the service then shows the last page.
     */
    private static int parsePage(String page) {
        try {
            int n = Integer.parseInt(page == null ? "" : page.trim());
            return Math.min(Math.max(n, 1), Integer.MAX_VALUE / NotificationService.PAGE_SIZE);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
