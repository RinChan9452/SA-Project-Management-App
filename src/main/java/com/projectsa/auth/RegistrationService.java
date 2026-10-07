package com.projectsa.auth;

import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.employee.Emails;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.EngineerSkill;
import com.projectsa.employee.Passwords;
import com.projectsa.employee.Role;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC00 Register Employee. */
@Service
public class RegistrationService {

    static final int MAX_SKILL_LENGTH = EngineerSkill.MAX_LENGTH;

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(EmployeeRepository employees, PasswordEncoder passwordEncoder) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks all UC00 business rules at once (field-level annotations are checked by the controller).
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validate(RegisterForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getRole() == null) {
            errors.add(new FieldMessage("role", "Please choose a role"));
        }
        if (form.getPassword() == null || form.getPassword().length() < 8) {
            errors.add(new FieldMessage("password", "Password must be at least 8 characters"));
        } else if (Passwords.isTooLong(form.getPassword())) {
            errors.add(new FieldMessage("password", Passwords.TOO_LONG));
        }
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            errors.add(new FieldMessage("confirmPassword", "Passwords do not match"));
        }
        String email = Emails.normalize(form.getEmail());
        if (email == null || email.isEmpty()) {
            errors.add(new FieldMessage("email", "Email is required"));
        } else if (employees.existsByEmail(email)) {
            errors.add(new FieldMessage("email", "This email is already registered"));
        }
        if (form.getRole() == Role.TECH) {
            List<String> skills = FieldValidationException.collect(errors, () -> cleanSkills(form.getSkills()));
            if (skills != null && skills.isEmpty()) {
                errors.add(new FieldMessage("skills", "Please add at least one skill"));
            }
        }
        FieldValidationException.throwIfAny(errors);
    }

    /**
     * Validates the business rules and saves the employee.
     *
     * @throws FieldValidationException when a rule fails
     */
    @Transactional
    public Employee register(RegisterForm form) {
        validate(form);
        String email = Emails.normalize(form.getEmail());
        List<String> skills = form.getRole() == Role.TECH ? cleanSkills(form.getSkills()) : List.of();

        Employee employee = new Employee(form.getName().trim(), email,
                passwordEncoder.encode(form.getPassword()), form.getRole());
        skills.forEach(employee::addSkill);
        try {
            return employees.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email at the same time
            throw new FieldValidationException("email", "This email is already registered");
        }
    }

    /** Trims, drops blanks, and rejects duplicates (case-insensitive) and over-long skills. */
    static List<String> cleanSkills(List<String> raw) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String skill = s.trim();
            if (skill.length() > MAX_SKILL_LENGTH) {
                throw new FieldValidationException("skills", "A skill must be at most " + MAX_SKILL_LENGTH + " characters");
            }
            if (!seen.add(skill.toLowerCase(Locale.ROOT))) {
                throw new FieldValidationException("skills", "Duplicate skill: " + skill);
            }
            result.add(skill);
        }
        return result;
    }
}
