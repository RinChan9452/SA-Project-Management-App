package com.projectsa.testschedule;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.project.Project;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

/** UC05 Set Project Test Calendar Notification. */
@Controller
@PreAuthorize("hasRole('TECH')")
public class TestScheduleController {

    private final TestScheduleService scheduleService;

    public TestScheduleController(TestScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/projects/{id}/test-schedule")
    String form(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("form", new TestScheduleForm());
        return show(scheduleService.projectForSchedule(id, user), model);
    }

    @PostMapping("/projects/{id}/test-schedule")
    String save(@PathVariable Long id, @Valid @ModelAttribute("form") TestScheduleForm form, BindingResult result,
                @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            if (result.hasErrors()) {
                scheduleService.validate(id, form, user);   // show "must be in the future" in the same round
            } else {
                TestSchedule saved = scheduleService.save(id, form, user);
                redirect.addFlashAttribute("success", "The customer test is set for "
                        + saved.getTestAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH))
                        + ". Sale, PM and engineers were notified. The project is now Testing.");
                return "redirect:/projects/" + id;
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(scheduleService.projectForSchedule(id, user), model);
    }

    private String show(Project project, Model model) {
        model.addAttribute("project", project);
        return "testschedule/form";
    }
}
