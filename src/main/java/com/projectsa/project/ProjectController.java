package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectController {

    private final ProjectService projectService;
    private final EmployeeRepository employees;

    public ProjectController(ProjectService projectService, EmployeeRepository employees) {
        this.projectService = projectService;
        this.employees = employees;
    }

    /** UC01 form. Sale in charge defaults to the current user. */
    @GetMapping("/projects/new")
    @PreAuthorize("hasRole('SALE')")
    String newProject(@AuthenticationPrincipal CurrentUser user, Model model) {
        ProjectForm form = new ProjectForm();
        form.setSaleId(user.getId());
        model.addAttribute("form", form);
        addPeople(model);
        return "project/new";
    }

    @PostMapping("/projects")
    @PreAuthorize("hasRole('SALE')")
    String create(@Valid @ModelAttribute("form") ProjectForm form, BindingResult result,
                  @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            if (result.hasErrors()) {
                projectService.validate(form, user);   // show the business-rule errors in the same round
            } else {
                Project project = projectService.create(form, user);
                redirect.addFlashAttribute("success",
                        "Project \"" + project.getProjectName() + "\" was created (ID " + project.getId() + ").");
                return "redirect:/projects/" + project.getId();
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        addPeople(model);
        return "project/new";
    }

    private void addPeople(Model model) {
        model.addAttribute("sales", employees.findByRoleOrderByName(Role.SALE));
        model.addAttribute("presales", employees.findByRoleOrderByName(Role.PRESALE));
    }
}
