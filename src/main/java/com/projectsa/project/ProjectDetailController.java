package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.document.DocumentService;
import com.projectsa.requirement.ApprovalService;
import com.projectsa.requirement.Requirement;
import com.projectsa.requirement.RequirementService;
import com.projectsa.solution.SolutionService;
import com.projectsa.testschedule.TestScheduleService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** UC03 Check Project Detail with the F3 search &amp; filter list — every logged-in role, read-only. */
@Controller
public class ProjectDetailController {

    private final ProjectService projectService;
    private final DocumentService documentService;
    private final TestScheduleService scheduleService;
    private final RequirementService requirementService;
    private final ProjectListService listService;

    public ProjectDetailController(ProjectService projectService, DocumentService documentService,
                                   TestScheduleService scheduleService, RequirementService requirementService,
                                   ProjectListService listService) {
        this.projectService = projectService;
        this.documentService = documentService;
        this.scheduleService = scheduleService;
        this.requirementService = requirementService;
        this.listService = listService;
    }

    /** F3: search box (ID, project name, customer) + filters (status, Sale in charge, only mine), 20 per page. */
    @GetMapping("/projects")
    String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                @RequestParam(required = false) String saleId, @RequestParam(required = false) String mine,
                @RequestParam(required = false) String page,
                @AuthenticationPrincipal CurrentUser user, Model model) {
        ProjectSearch search = ProjectSearch.of(q, status, saleId, mine, page);
        model.addAttribute("search", search);
        model.addAttribute("rows", listService.search(search, user));
        model.addAttribute("statuses", ProjectStatus.values());
        model.addAttribute("sales", listService.sales());
        return "project/list";
    }

    @GetMapping("/projects/{id}")
    String detail(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, Model model) {
        Project project = projectService.detail(id);
        model.addAttribute("project", project);
        model.addAttribute("documents", documentService.publicDocuments(id));
        List<ProjectEngineer> engineers = projectService.engineersOf(id);
        model.addAttribute("engineers", engineers);
        model.addAttribute("testSchedules", scheduleService.schedulesOf(id));
        List<Requirement> requirements = requirementService.requirementsOf(id);
        model.addAttribute("requirements", requirements);
        model.addAttribute("attachments", documentService.attachmentsByRequirement(id));
        // Requirements whose attachments this user may download (the download checks the same rule again)
        Set<Long> openAttachments = requirements.stream()
                .filter(r -> DocumentService.mayDownloadAttachments(r, project, user))
                .map(Requirement::getId).collect(Collectors.toSet());
        model.addAttribute("openAttachments", openAttachments);
        // Action buttons (the services check the same rules again)
        model.addAttribute("canStoreSolution", SolutionService.canStoreSolution(project, user));
        model.addAttribute("canAssignEngineers", AssignEngineerService.canAssign(project, user));
        model.addAttribute("canSetTestDate", TestScheduleService.canSetTestDate(project, engineers, user));
        LocalDateTime latestTestAt = requirementService.latestTestAt(id);
        model.addAttribute("canAddRequirement", RequirementService.canAddRequirement(project, latestTestAt, user));
        boolean canComplete = CompleteProjectService.canComplete(project, latestTestAt, user);
        model.addAttribute("canComplete", canComplete);
        // PM in charge of a project in Testing: say from when Complete Project is available
        boolean pmInCharge = project.getPm() != null && project.getPm().getId().equals(user.getId());
        model.addAttribute("completeFrom", !canComplete && pmInCharge && project.getStatus() == ProjectStatus.TESTING
                ? CompleteProjectService.completeFrom(latestTestAt) : null);
        // UC07: the pending requirement the Sale in charge can approve or reject
        model.addAttribute("toDecide", requirements.stream()
                .filter(r -> ApprovalService.canDecide(r, project, user)).findFirst().orElse(null));
        return "project/detail";
    }
}
