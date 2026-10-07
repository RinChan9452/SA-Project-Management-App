package com.projectsa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.EngineerSkill;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RegistrationServiceTest {

    @Autowired
    RegistrationService service;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    PasswordEncoder passwordEncoder;

    static RegisterForm form(Role role, String email, String... skills) {
        RegisterForm f = new RegisterForm();
        f.setName("  Test User ");
        f.setRole(role);
        f.setEmail(email);
        f.setPassword("secret123");
        f.setConfirmPassword("secret123");
        f.setSkills(new ArrayList<>(List.of(skills)));
        return f;
    }

    @Test
    void registersTechEngineerWithSkillsAndHashedPassword() {
        Employee e = service.register(form(Role.TECH, " Tech@Example.com ", "CCNA", " AWS "));

        Employee saved = employees.findById(e.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Test User");
        assertThat(saved.getEmail()).isEqualTo("tech@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.TECH);
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getSkills()).extracting(EngineerSkill::getSkill).containsExactlyInAnyOrder("CCNA", "AWS");
    }

    @Test
    void allProblemsReportedAtOnce() {
        service.register(form(Role.SALE, "taken@example.com"));
        RegisterForm f = form(Role.TECH, "TAKEN@example.com");
        f.setPassword("short");
        f.setConfirmPassword("different");

        assertThatThrownBy(() -> service.register(f))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldValidationException.FieldMessage::field)
                        .containsExactly("password", "confirmPassword", "email", "skills"));
        assertThat(employees.count()).isEqualTo(1);
    }

    @Test
    void nonTechRolesDoNotSaveSkills() {
        Employee e = service.register(form(Role.SALE, "sale@example.com", "CCNA"));
        assertThat(employees.findById(e.getId()).orElseThrow().getSkills()).isEmpty();
    }

    @Test
    void emailMustBeUniqueIgnoringCase() {
        service.register(form(Role.PM, "pm@example.com"));
        assertThatThrownBy(() -> service.register(form(Role.SALE, "PM@Example.COM")))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "email");
    }

    @Test
    void techEngineerNeedsAtLeastOneSkill() {
        assertThatThrownBy(() -> service.register(form(Role.TECH, "t@example.com", "  ")))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "skills");
    }

    @Test
    void duplicateSkillsRejectedIgnoringCase() {
        assertThatThrownBy(() -> service.register(form(Role.TECH, "t@example.com", "CCNA", "ccna")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("Duplicate skill");
    }

    @Test
    void passwordsMustMatch() {
        RegisterForm f = form(Role.PRESALE, "p@example.com");
        f.setConfirmPassword("different1");
        assertThatThrownBy(() -> service.register(f))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "confirmPassword");
    }

    @Test
    void shortPasswordRejected() {
        RegisterForm f = form(Role.PRESALE, "p@example.com");
        f.setPassword("short");
        f.setConfirmPassword("short");
        assertThatThrownBy(() -> service.register(f))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "password");
    }
}
