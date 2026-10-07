package com.projectsa.employee;

import com.projectsa.auth.CurrentUser;
import com.projectsa.auth.CurrentUserSession;
import com.projectsa.common.FieldValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * F4 Edit my profile — every logged-in role, own account only. Each part of the page (name, password, picture,
 * skills) is its own form, so an error in one never loses what was typed in another.
 */
@Controller
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserSession session;

    public ProfileController(ProfileService profileService, CurrentUserSession session) {
        this.profileService = profileService;
        this.session = session;
    }

    @GetMapping("/profile")
    String profile(@AuthenticationPrincipal CurrentUser user, Model model) {
        return show(user, model);
    }

    @PostMapping("/profile/name")
    String changeName(@Valid @ModelAttribute("nameForm") NameForm form, BindingResult result,
                      @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect,
                      HttpServletRequest request, HttpServletResponse response) {
        if (!result.hasErrors()) {
            try {
                session.refresh(profileService.changeName(user, form), request, response);
                redirect.addFlashAttribute("success", "Your name was changed.");
                return "redirect:/profile";
            } catch (FieldValidationException e) {
                e.rejectInto(result);
            }
        }
        return show(user, model);
    }

    @PostMapping("/profile/password")
    String changePassword(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
                          @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect,
                          HttpServletRequest request, HttpServletResponse response) {
        try {
            if (result.hasErrors()) {
                profileService.validatePassword(user, form);   // show the business-rule errors in the same round
            } else {
                session.refresh(profileService.changePassword(user, form), request, response);
                redirect.addFlashAttribute("success", "Your password was changed.");
                return "redirect:/profile";
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        // Never send passwords back to the browser
        form.setCurrentPassword(null);
        form.setNewPassword(null);
        form.setConfirmPassword(null);
        return show(user, model);
    }

    @PostMapping("/profile/picture")
    String changePicture(@ModelAttribute("pictureForm") PictureForm form, BindingResult result,
                         @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect,
                         HttpServletRequest request, HttpServletResponse response) {
        try {
            session.refresh(profileService.changePicture(user, form.getPicture()), request, response);
            redirect.addFlashAttribute("success", "Your profile picture was changed.");
            return "redirect:/profile";
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(user, model);
    }

    @PostMapping("/profile/skills")
    String addSkill(@ModelAttribute("skillForm") SkillForm form, BindingResult result,
                    @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        try {
            profileService.addSkill(user, form);
            redirect.addFlashAttribute("success", "Skill added: " + form.getSkill().trim());
            return "redirect:/profile";
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return show(user, model);
    }

    /** The only delete in the app (CLAUDE.md §7): a Tech Engineer removing one of their own skills. */
    @PostMapping("/profile/skills/{skillId}/remove")
    String removeSkill(@PathVariable Long skillId, @AuthenticationPrincipal CurrentUser user,
                       RedirectAttributes redirect) {
        try {
            String removed = profileService.removeSkill(user, skillId);
            redirect.addFlashAttribute("success", "Skill removed: " + removed);
        } catch (FieldValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    private String show(CurrentUser user, Model model) {
        Employee employee = profileService.profile(user);
        model.addAttribute("employee", employee);
        if (!model.containsAttribute("nameForm")) {
            NameForm nameForm = new NameForm();
            nameForm.setName(employee.getName());
            model.addAttribute("nameForm", nameForm);
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        if (!model.containsAttribute("pictureForm")) {
            model.addAttribute("pictureForm", new PictureForm());
        }
        if (!model.containsAttribute("skillForm")) {
            model.addAttribute("skillForm", new SkillForm());
        }
        model.addAttribute("maxPictureMb", ProfileService.MAX_PICTURE_BYTES / (1024 * 1024));
        return "employee/profile";
    }
}
