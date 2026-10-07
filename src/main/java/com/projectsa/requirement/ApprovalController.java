package com.projectsa.requirement;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.document.DocumentService;
import com.projectsa.project.ProjectService;
import com.projectsa.testschedule.TestScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC07 Approve New Project Requirement. The list of pending requirements (step 1) is on the SALE dashboard. */
@Controller
@PreAuthorize("hasRole('SALE')")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ProjectService projectService;
    private final TestScheduleService scheduleService;
    private final DocumentService documentService;

    public ApprovalController(ApprovalService approvalService, ProjectService projectService,
                              TestScheduleService scheduleService, DocumentService documentService) {
        this.approvalService = approvalService;
        this.projectService = projectService;
        this.scheduleService = scheduleService;
        this.documentService = documentService;
    }

    @GetMapping("/requirements/{id}/decision")
    String form(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, Model model) {
        Requirement requirement = approvalService.requirementForDecision(id, user);
        ApprovalForm form = new ApprovalForm();
        form.setVersion(requirement.getVersion());
        model.addAttribute("form", form);
        return show(requirement, model);
    }

    @PostMapping("/requirements/{id}/decision")
    String decide(@PathVariable Long id, @Valid @ModelAttribute("form") ApprovalForm form, BindingResult result,
                  @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            if (result.hasErrors()) {
                approvalService.validate(id, form, user);   // show "reason required" in the same round
            } else {
                Requirement decided = approvalService.decide(id, form, user);
                redirect.addFlashAttribute("success", decided.getStatus() == RequirementStatus.APPROVED
                        ? "The requirement \"" + decided.getTitle() + "\" was approved. The PM, the Presale Engineer and the"
                          + " assigned engineers were notified. The project is back to New Project."
                        : "The requirement \"" + decided.getTitle() + "\" was rejected. The PM was notified."
                          + " The project is now Finished.");
                return "redirect:/projects/" + decided.getProject().getId();
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(approvalService.requirementForDecision(id, user), model);
    }

    /** UC07 step 2: requirement detail with attachments, next to the project it belongs to. */
    private String show(Requirement requirement, Model model) {
        Long projectId = requirement.getProject().getId();
        model.addAttribute("requirement", requirement);
        model.addAttribute("project", requirement.getProject());
        model.addAttribute("engineers", projectService.engineersOf(projectId));
        model.addAttribute("testSchedules", scheduleService.schedulesOf(projectId));
        model.addAttribute("attachments",
                documentService.attachmentsByRequirement(projectId).getOrDefault(requirement.getId(), List.of()));
        return "requirement/decide";
    }
}
