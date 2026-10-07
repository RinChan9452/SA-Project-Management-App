package com.projectsa.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;

    @Test
    void protectedPagesRedirectToLogin() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginAndRegisterPagesArePublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Please Assign Your Skills :")));
    }

    @Test
    void registerThenLoginThenSeeDashboard() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Nina Tech")
                        .param("role", "TECH")
                        .param("email", "nina@example.com")
                        .param("password", "password1")
                        .param("confirmPassword", "password1")
                        .param("skills", "CCNA", "Fortinet NSE4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        mvc.perform(formLogin("/login").user("email", "NINA@example.com").password("password1"))
                .andExpect(authenticated().withRoles("TECH"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("email", "nobody@example.com").password("whatever1"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void invalidRegistrationShowsErrorsAndSavesNothing() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "")
                        .param("email", "not-an-email")
                        .param("password", "password1")
                        .param("confirmPassword", "password2"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "role", "email"));

        mvc.perform(post("/register").with(csrf())
                        .param("name", "Tom")
                        .param("role", "PM")
                        .param("email", "tom@example.com")
                        .param("password", "password1")
                        .param("confirmPassword", "password2"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));

        org.assertj.core.api.Assertions.assertThat(employees.count()).isZero();
    }

    /** BCrypt refuses more than 72 bytes; 30 Thai letters are only 30 characters but 90 bytes. */
    @Test
    void passwordOver72BytesIsAFormErrorNotACrash() throws Exception {
        String thai30 = "ก".repeat(30);
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Somchai")
                        .param("role", "PM")
                        .param("email", "somchai@example.com")
                        .param("password", thai30)
                        .param("confirmPassword", thai30))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "password"))
                .andExpect(content().string(Matchers.containsString("Password is too long")));
        org.assertj.core.api.Assertions.assertThat(employees.count()).isZero();

        String thai24 = "ก".repeat(24);   // exactly 72 bytes is still fine
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Somchai")
                        .param("role", "PM")
                        .param("email", "somchai@example.com")
                        .param("password", thai24)
                        .param("confirmPassword", thai24))
                .andExpect(redirectedUrl("/login?registered"));
        mvc.perform(formLogin("/login").user("email", "somchai@example.com").password(thai24))
                .andExpect(authenticated().withRoles("PM"));
    }

    @Test
    void emailWithSpacesAroundItIsAccepted() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Nina")
                        .param("role", "PM")
                        .param("email", "  Nina@Example.com ")
                        .param("password", "password1")
                        .param("confirmPassword", "password1"))
                .andExpect(redirectedUrl("/login?registered"));
        org.assertj.core.api.Assertions.assertThat(employees.findByEmail("nina@example.com")).isPresent();
    }

    @Test
    void allErrorsAreShownInOneRound() throws Exception {
        employees.save(new Employee("Taken", "taken@example.com", "{noop}x", Role.SALE));

        // Blank name (annotation) + email taken + passwords differ + Tech without skills (business rules)
        mvc.perform(post("/register").with(csrf())
                        .param("name", "")
                        .param("role", "TECH")
                        .param("email", "Taken@example.com")
                        .param("password", "password1")
                        .param("confirmPassword", "password2"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "email", "confirmPassword", "skills"));
    }

    @Test
    void tamperedRoleShowsPlainMessage() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Eve")
                        .param("role", "ADMIN")
                        .param("email", "eve@example.com")
                        .param("password", "password1")
                        .param("confirmPassword", "password1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "role"))
                .andExpect(content().string(Matchers.containsString("Please choose a role")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
        org.assertj.core.api.Assertions.assertThat(employees.count()).isZero();
    }

    @Test
    void registerWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/register").param("name", "x"))
                .andExpect(status().isForbidden());
    }
}
