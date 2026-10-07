package com.projectsa.auth;

import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @ModelAttribute("roles")
    Role[] roles() {
        return Role.values();
    }

    @GetMapping("/")
    String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    String login(Authentication auth) {
        return isLoggedIn(auth) ? "redirect:/dashboard" : "auth/login";
    }

    @GetMapping("/register")
    String registerForm(Authentication auth, Model model) {
        if (isLoggedIn(auth)) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result) {
        try {
            if (result.hasErrors()) {
                registrationService.validate(form);   // show the business-rule errors in the same round
            } else {
                registrationService.register(form);
                return "redirect:/login?registered";
            }
        } catch (FieldValidationException e) {
            e.rejectInto(result);
        }
        return "auth/register";
    }

    private static boolean isLoggedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
