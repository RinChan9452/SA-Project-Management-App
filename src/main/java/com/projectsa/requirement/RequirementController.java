package com.projectsa.requirement;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectService;
import jakarta.validation.Valid;
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

/** UC06 Add Customer New Requirement. */
@Controller
@PreAuthorize("hasRole('PM')")
public class RequirementController {

    private final RequirementService requirementService;
    private final ProjectService projectService;

    public RequirementController(RequirementService requirementService, ProjectService projectService) {
        this.requirementService = requirementService;
        this.projectService = projectService;
    }

    @GetMapping("/projects/{id}/requirements/new")
    String form(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, Model model) {
        Project project = requirementService.projectForRequirement(id, user);
        RequirementForm form = new RequirementForm();
        form.setPriority(Priority.MEDIUM);
        model.addAttribute("form", form);
        return show(project, model);
    }

    @PostMapping("/projects/{id}/requirements")
    String save(@PathVariable Long id, @Valid @ModelAttribute("form") RequirementForm form, BindingResult result,
                @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            if (result.hasErrors()) {
                requirementService.validate(id, form, user);   // show the due date/attachment errors in the same round
            } else {
                Requirement saved = requirementService.save(id, form, user);
                redirect.addFlashAttribute("success", "The requirement \"" + saved.getTitle()
                        + "\" was sent to the Sale in charge for approval. The project now waits for approval.");
                return "redirect:/projects/" + id;
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(requirementService.projectForRequirement(id, user), model);
    }

    /** UC06 step 3: project details, engineers and earlier requirements next to the form. */
    private String show(Project project, Model model) {
        model.addAttribute("project", project);
        model.addAttribute("engineers", projectService.engineersOf(project.getId()));
        model.addAttribute("testAt", requirementService.latestTestAt(project.getId()));
        model.addAttribute("requirements", requirementService.requirementsOf(project.getId()));
        model.addAttribute("priorities", Priority.values());
        return "requirement/form";
    }
}
