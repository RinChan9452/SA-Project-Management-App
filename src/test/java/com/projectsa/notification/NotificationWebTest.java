package com.projectsa.notification;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectWaitingForEngineers;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** F2 notification bell + list over HTTP: only my own, only sent ones, open marks read, mark all, paging. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    NotificationService notificationService;
    @Autowired
    NotificationRepository notifications;

    Employee sale;
    Employee me;
    Employee other;
    Project project;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        Employee presale = employee(employees, "Pat Presale", Role.PRESALE);
        me = employee(employees, "Anna Tech", Role.TECH);
        other = employee(employees, "Ben Tech", Role.TECH);
        project = projectWaitingForEngineers(projectService, projects, sale, presale);
    }

    private Notification send(Employee to, String message, LocalDateTime sendAt) {
        return notificationService.notifyAt(to, project, NotificationType.ASSIGNED, message, sendAt);
    }

    @Test
    void bellCountsOnlyMyUnreadAlreadySentNotifications() throws Exception {
        send(me, "first", LocalDateTime.now().minusHours(2));
        send(me, "second", LocalDateTime.now().minusHours(1));
        Notification read = send(me, "already read", LocalDateTime.now().minusHours(3));
        notificationService.open(read.getId(), user(me));
        send(me, "reminder later", LocalDateTime.now().plusDays(1));          // not sent yet
        send(other, "not mine", LocalDateTime.now().minusMinutes(5));

        for (String page : new String[] {"/dashboard", "/projects", "/projects/" + project.getId(), "/profile"}) {
            mvc.perform(get(page).with(user(user(me))))
                    .andExpect(model().attribute("unreadCount", 2L))
                    .andExpect(content().string(Matchers.containsString("id=\"unreadBadge\"")))
                    .andExpect(content().string(Matchers.containsString("2 unread notifications")));
        }
        mvc.perform(get("/dashboard").with(user(user(sale))))
                .andExpect(model().attribute("unreadCount", 0L))
                .andExpect(content().string(Matchers.containsString("href=\"/notifications\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("id=\"unreadBadge\""))));
    }

    @Test
    void reminderShowsOnceItsSendTimeHasCome() throws Exception {
        Notification later = send(me, "Test tomorrow", LocalDateTime.now().plusDays(1));
        mvc.perform(get("/notifications").with(user(user(me))))
                .andExpect(model().attribute("unreadCount", 0L))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Test tomorrow"))));
        mvc.perform(post("/notifications/{id}/open", later.getId()).with(csrf()).with(user(user(me))))
                .andExpect(status().isNotFound());

        send(me, "Test reminder now", LocalDateTime.now().minusSeconds(1));
        mvc.perform(get("/notifications").with(user(user(me))))
                .andExpect(model().attribute("unreadCount", 1L))
                .andExpect(content().string(Matchers.containsString("Test reminder now")));
    }

    @Test
    void listShowsOnlyMineNewestFirst() throws Exception {
        send(me, "older message", LocalDateTime.now().minusDays(2));
        send(me, "newest message", LocalDateTime.now().minusMinutes(1));
        send(me, "middle message", LocalDateTime.now().minusDays(1));
        send(other, "someone else's message", LocalDateTime.now());

        String html = mvc.perform(get("/notifications").with(user(user(me))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("someone else"))))
                .andExpect(content().string(Matchers.containsString("Mark all as read")))
                .andExpect(content().string(Matchers.containsString("Assigned to project")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("newest message")).isLessThan(html.indexOf("middle message"));
        assertThat(html.indexOf("middle message")).isLessThan(html.indexOf("older message"));
    }

    @Test
    void openingMarksReadAndOpensTheProject() throws Exception {
        Notification n = send(me, "You were assigned", LocalDateTime.now().minusMinutes(1));
        mvc.perform(post("/notifications/{id}/open", n.getId()).with(csrf()).with(user(user(me))))
                .andExpect(redirectedUrl("/projects/" + project.getId()));
        LocalDateTime readAt = notifications.findById(n.getId()).orElseThrow().getReadAt();
        assertThat(readAt).isNotNull();

        // opening again still works and keeps the first read time
        mvc.perform(post("/notifications/{id}/open", n.getId()).with(csrf()).with(user(user(me))))
                .andExpect(redirectedUrl("/projects/" + project.getId()));
        assertThat(notifications.findById(n.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
        mvc.perform(get("/dashboard").with(user(user(me))))
                .andExpect(model().attribute("unreadCount", 0L));
    }

    @Test
    void notificationWithoutProjectGoesBackToTheList() throws Exception {
        Notification n = notificationService.notifyAt(me, null, NotificationType.ASSIGNED, "general message",
                LocalDateTime.now().minusSeconds(1));
        mvc.perform(post("/notifications/{id}/open", n.getId()).with(csrf()).with(user(user(me))))
                .andExpect(redirectedUrl("/notifications"));
    }

    @Test
    void cannotOpenSomeoneElsesNotification() throws Exception {
        Notification theirs = send(other, "for Ben", LocalDateTime.now().minusMinutes(1));
        mvc.perform(post("/notifications/{id}/open", theirs.getId()).with(csrf()).with(user(user(me))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notifications/{id}/open", 999_999).with(csrf()).with(user(user(me))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notifications/{id}/open", theirs.getId()).with(user(user(other))))   // no CSRF token
                .andExpect(status().isForbidden());
        mvc.perform(get("/notifications/{id}/open", theirs.getId()).with(user(user(other))))
                .andExpect(status().isMethodNotAllowed());
        assertThat(notifications.findById(theirs.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void markAllAsReadOnlyTouchesMyAlreadySentNotifications() throws Exception {
        Notification a = send(me, "a", LocalDateTime.now().minusHours(1));
        Notification b = send(me, "b", LocalDateTime.now().minusMinutes(1));
        Notification later = send(me, "later", LocalDateTime.now().plusDays(1));
        Notification theirs = send(other, "theirs", LocalDateTime.now().minusMinutes(1));

        mvc.perform(post("/notifications/read-all").with(csrf()).with(user(user(me))))
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attribute("success", "2 notifications marked as read."));
        assertThat(notifications.findById(a.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notifications.findById(b.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notifications.findById(later.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(notifications.findById(theirs.getId()).orElseThrow().getReadAt()).isNull();

        mvc.perform(post("/notifications/read-all").with(csrf()).with(user(user(me))))
                .andExpect(flash().attribute("success", "You have no unread notifications."));
        mvc.perform(post("/notifications/read-all").with(user(user(me))))                       // no CSRF token
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagingTwentyPerPageAndLenientPageNumbers() throws Exception {
        for (int i = 0; i < 25; i++) {
            send(me, "message " + i, LocalDateTime.now().minusMinutes(i + 1));
        }
        Page<Notification> first = (Page<Notification>) mvc.perform(get("/notifications").with(user(user(me))))
                .andExpect(model().attribute("last", 2))
                .andReturn().getModelAndView().getModel().get("notifications");
        assertThat(first.getContent()).hasSize(20);
        assertThat(first.getContent().get(0).getMessage()).isEqualTo("message 0");

        mvc.perform(get("/notifications").param("page", "2").with(user(user(me))))
                .andExpect(model().attribute("current", 2))
                .andExpect(content().string(Matchers.containsString("message 24")))
                .andExpect(content().string(Matchers.containsString("Page 2 of 2")));
        mvc.perform(get("/notifications").param("page", "abc").with(user(user(me))))
                .andExpect(status().isOk()).andExpect(model().attribute("current", 1));
        mvc.perform(get("/notifications").param("page", "99").with(user(user(me))))
                .andExpect(status().isOk()).andExpect(model().attribute("current", 2));
        // so large that the row offset would not fit in an int: still the last page, not an error
        mvc.perform(get("/notifications").param("page", "2147483647").with(user(user(me))))
                .andExpect(status().isOk()).andExpect(model().attribute("current", 2));
    }

    @Test
    void emptyListAndLoginRequired() throws Exception {
        mvc.perform(get("/notifications").with(user(user(me))))
                .andExpect(content().string(Matchers.containsString("You have no notifications yet.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Mark all as read"))));
        mvc.perform(get("/notifications")).andExpect(redirectedUrl("/login"));
    }
}
