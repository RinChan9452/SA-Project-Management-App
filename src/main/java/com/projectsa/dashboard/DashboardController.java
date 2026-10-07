package com.projectsa.dashboard;

import com.projectsa.auth.CurrentUser;
import com.projectsa.notification.NotificationService;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import com.projectsa.requirement.ApprovalService;
import com.projectsa.requirement.RequirementService;
import com.projectsa.testschedule.TestScheduleService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** F1 Dashboard: each role's task lists and projects, plus my latest unread notifications (F2). */
@Controller
public class DashboardController {

    private final ProjectService projectService;
    private final TestScheduleService scheduleService;
    private final RequirementService requirementService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;

    public DashboardController(ProjectService projectService, TestScheduleService scheduleService,
                               RequirementService requirementService, ApprovalService approvalService,
                               NotificationService notificationService) {
        this.projectService = projectService;
        this.scheduleService = scheduleService;
        this.requirementService = requirementService;
        this.approvalService = approvalService;
        this.notificationService = notificationService;
    }

    @GetMapping("/dashboard")
    String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("latestNotifications", notificationService.latestUnread(user));
        switch (user.getRole()) {
            case SALE -> {
                model.addAttribute("toApprove", approvalService.pendingForSale(user));
                var mine = projectService.projectsOfSale(user.getId());
                model.addAttribute("statusCounts", countByStatus(mine));
                model.addAttribute("myProjects", mine);
                model.addAttribute("myProjectsTitle", "My projects (Sale in charge)");
            }
            case PRESALE -> {
                var mine = projectService.projectsOfPresale(user.getId());
                model.addAttribute("needSolution", mine.stream().filter(p -> p.getStatus() == ProjectStatus.NEW_PROJECT).toList());
                model.addAttribute("myProjects", mine.stream().filter(p -> p.getStatus() != ProjectStatus.NEW_PROJECT).toList());
                model.addAttribute("myProjectsTitle", "My other projects (Presale in charge)");
            }
            case PM -> {
                model.addAttribute("needEngineers", projectService.projectsNeedingEngineers(user));
                model.addAttribute("myTesting", requirementService.myTestingProjects(user));
                model.addAttribute("myRequirements", requirementService.createdBy(user));
                model.addAttribute("myProjects", projectService.projectsOfPm(user.getId()));
                model.addAttribute("myProjectsTitle", "My projects (PM in charge)");
            }
            case TECH -> {
                model.addAttribute("needTestDate", scheduleService.needTestDate(user));
                model.addAttribute("upcomingTests", scheduleService.upcomingTests(user));
                model.addAttribute("myProjects", projectService.projectsOfEngineer(user.getId()));
                model.addAttribute("myProjectsTitle", "My assigned projects");
            }
        }
        return "dashboard";
    }

    /** Every status in §6 order, with how many of these projects are in it (0 included). */
    static Map<ProjectStatus, Long> countByStatus(List<Project> projects) {
        Map<ProjectStatus, Long> counts = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            counts.put(status, 0L);
        }
        projects.forEach(p -> counts.merge(p.getStatus(), 1L, Long::sum));
        return counts;
    }
}
