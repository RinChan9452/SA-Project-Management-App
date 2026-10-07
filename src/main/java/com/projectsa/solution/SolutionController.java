package com.projectsa.solution;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.document.DocumentType;
import com.projectsa.project.Project;
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

/** UC02 Store Project Solution Detail. */
@Controller
@PreAuthorize("hasRole('PRESALE')")
public class SolutionController {

    private final SolutionService solutionService;

    public SolutionController(SolutionService solutionService) {
        this.solutionService = solutionService;
    }

    @GetMapping("/projects/{id}/solution")
    String form(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, Model model) {
        Project project = solutionService.projectForSolution(id, user);
        SolutionForm form = new SolutionForm();
        // Coming back after an approved requirement: pre-fill what was stored before
        form.setStartDate(project.getStartDate());
        form.setEndDate(project.getEndDate());
        form.setObjective(project.getObjective());
        form.setEstBudget(project.getEstBudget());
        model.addAttribute("form", form);
        return show(project, model);
    }

    @PostMapping("/projects/{id}/solution")
    String save(@PathVariable Long id, @Valid @ModelAttribute("form") SolutionForm form, BindingResult result,
                @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            if (result.hasErrors()) {
                solutionService.validate(id, form, user);   // show the date/PDF errors in the same round
            } else {
                solutionService.save(id, form, user);
                redirect.addFlashAttribute("success", "The solution was saved. The project now waits for engineer assignment.");
                return "redirect:/projects/" + id;
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(solutionService.projectForSolution(id, user), model);
    }

    private String show(Project project, Model model) {
        model.addAttribute("project", project);
        model.addAttribute("hasSolution", solutionService.hasDocument(project.getId(), DocumentType.SOLUTION));
        model.addAttribute("hasProductRequirement",
                solutionService.hasDocument(project.getId(), DocumentType.PRODUCT_REQUIREMENT));
        return "solution/form";
    }
}
