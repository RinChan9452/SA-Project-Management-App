package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Complete Project (TESTING → FINISH). The confirmation dialog is on the UC03 page. */
@Controller
@PreAuthorize("hasRole('PM')")
public class CompleteProjectController {

    private final CompleteProjectService completeService;

    public CompleteProjectController(CompleteProjectService completeService) {
        this.completeService = completeService;
    }

    @PostMapping("/projects/{id}/complete")
    String complete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        Project project = completeService.complete(id, user);
        redirect.addFlashAttribute("success", "The project \"" + project.getProjectName()
                + "\" is now Finished. Sale, Presale and engineers were notified.");
        return "redirect:/projects/" + id;
    }
}
