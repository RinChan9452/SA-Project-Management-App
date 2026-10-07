package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.Employee;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** UC04 Assign Engineer to Project. */
@Controller
@PreAuthorize("hasRole('PM')")
public class AssignEngineerController {

    private final AssignEngineerService assignService;

    public AssignEngineerController(AssignEngineerService assignService) {
        this.assignService = assignService;
    }

    /** {@code skill} filters the list; {@code selected} keeps ticks when the filter changes (assign.js). */
    @GetMapping("/projects/{id}/assign")
    String form(@PathVariable Long id, @RequestParam(required = false) String skill,
                @RequestParam(required = false) List<Long> selected,
                @AuthenticationPrincipal CurrentUser user, Model model) {
        AssignForm form = new AssignForm();
        form.setSkill(skill);
        form.setEngineerIds(selected);
        model.addAttribute("form", form);
        return show(id, form, user, model);
    }

    @PostMapping("/projects/{id}/assign")
    String assign(@PathVariable Long id, @ModelAttribute("form") AssignForm form, BindingResult result,
                  @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                List<Employee> added = assignService.assign(id, form.getEngineerIds(), user);
                redirect.addFlashAttribute("success", added.isEmpty()
                        ? "Engineers confirmed. The project is now Working."
                        : "Assigned " + added.stream().map(Employee::getName).collect(Collectors.joining(", "))
                                + ". The project is now Working.");
                return "redirect:/projects/" + id;
            } catch (FieldValidationException e) {
                e.rejectInto(result);
            }
        }
        return show(id, form, user, model);
    }

    private String show(Long id, AssignForm form, CurrentUser user, Model model) {
        model.addAttribute("page", assignService.page(id, user, form.getSkill(), form.getEngineerIds()));
        return "project/assign";
    }
}
