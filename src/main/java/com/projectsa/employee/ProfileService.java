package com.projectsa.employee;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.common.NotFoundException;
import com.projectsa.document.FileKind;
import com.projectsa.document.FileStorage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * F4 Edit my profile. Every method works on the logged-in employee only (the ID always comes from the login,
 * never from the request), so role, email and ID cannot be changed and nobody can edit someone else.
 */
@Service
public class ProfileService {

    public static final long MAX_PICTURE_BYTES = 2L * 1024 * 1024;
    static final Set<FileKind> PICTURE_KINDS = EnumSet.of(FileKind.PNG, FileKind.JPG);
    static final String AVATAR_FOLDER = "avatars";

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final FileStorage storage;

    public ProfileService(EmployeeRepository employees, PasswordEncoder passwordEncoder, FileStorage storage) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

    /** The logged-in employee with their skills. */
    @Transactional(readOnly = true)
    public Employee profile(CurrentUser user) {
        return load(user);
    }

    /** Changes my name (trimmed). Annotation rules on {@link NameForm} are checked by the controller. */
    @Transactional
    public Employee changeName(CurrentUser user, NameForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            throw new FieldValidationException("name", "Name is required");
        }
        Employee employee = load(user);
        employee.setName(form.getName().trim());
        return employee;
    }

    /**
     * Checks the password business rules: current password correct, new one 8 to 72 characters and equal to the
     * confirmation.
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validatePassword(CurrentUser user, PasswordForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getCurrentPassword() != null && !form.getCurrentPassword().isEmpty()
                && !passwordEncoder.matches(form.getCurrentPassword(), load(user).getPasswordHash())) {
            errors.add(new FieldMessage("currentPassword", "Current password is incorrect"));
        }
        if (form.getNewPassword() == null || form.getNewPassword().length() < 8 || form.getNewPassword().length() > 72) {
            errors.add(new FieldMessage("newPassword", "Password must be 8 to 72 characters"));
        } else if (Passwords.isTooLong(form.getNewPassword())) {
            errors.add(new FieldMessage("newPassword", Passwords.TOO_LONG));
        }
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            errors.add(new FieldMessage("confirmPassword", "Passwords do not match"));
        }
        FieldValidationException.throwIfAny(errors);
    }

    /** Saves the new password as a BCrypt hash after {@link #validatePassword}. */
    @Transactional
    public Employee changePassword(CurrentUser user, PasswordForm form) {
        if (form.getCurrentPassword() == null || form.getCurrentPassword().isEmpty()) {
            throw new FieldValidationException("currentPassword", "Please enter your current password");
        }
        validatePassword(user, form);
        Employee employee = load(user);
        employee.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        return employee;
    }

    /**
     * Saves a new profile picture (JPG or PNG by content, at most 2 MB) under {@code uploads/avatars/} and replaces
     * the old one. The old file is deleted only after the change is saved; if saving fails, the new file is deleted.
     *
     * @throws FieldValidationException on {@code picture} when the file is missing, too large or not JPG/PNG
     */
    @Transactional
    public Employee changePicture(CurrentUser user, MultipartFile file) {
        FileKind kind = storage.check(file, PICTURE_KINDS, MAX_PICTURE_BYTES, "picture");
        Employee employee = load(user);
        String oldPath = employee.getProfilePicturePath();
        String newPath = storage.save(file, kind, AVATAR_FOLDER);
        employee.setProfilePicturePath(newPath);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (oldPath != null) {
                        storage.deleteQuietly(oldPath);
                    }
                } else {
                    storage.deleteQuietly(newPath);
                }
            }
        });
        return employee;
    }

    /**
     * Adds one skill to my list (Tech Engineer only): not blank, at most 100 characters, not one I already have
     * (ignoring upper/lower case).
     *
     * @throws FieldValidationException on {@code skill} when a rule fails
     */
    @Transactional
    public Employee addSkill(CurrentUser user, SkillForm form) {
        requireTech(user);
        Employee employee = load(user);
        String skill = form.getSkill() == null ? "" : form.getSkill().trim();
        if (skill.isEmpty()) {
            throw new FieldValidationException("skill", "Please enter a skill");
        }
        if (skill.length() > EngineerSkill.MAX_LENGTH) {
            throw new FieldValidationException("skill", "A skill must be at most " + EngineerSkill.MAX_LENGTH + " characters");
        }
        if (employee.hasSkill(skill)) {
            throw new FieldValidationException("skill", "You already have this skill: " + skill);
        }
        employee.addSkill(skill);
        try {
            return employees.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            // The same skill added twice at the same moment
            throw new FieldValidationException("skill", "You already have this skill: " + skill);
        }
    }

    /**
     * Removes one of my skills (Tech Engineer only). At least one skill must remain.
     *
     * @return the removed skill's name
     * @throws NotFoundException        if the skill is not one of mine
     * @throws FieldValidationException on {@code skill} when it is my last skill
     */
    @Transactional
    public String removeSkill(CurrentUser user, Long skillId) {
        requireTech(user);
        Employee employee = load(user);
        EngineerSkill skill = employee.getSkills().stream()
                .filter(s -> s.getId().equals(skillId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Skill not found"));
        if (employee.getSkills().size() <= 1) {
            throw new FieldValidationException("skill", "You must keep at least one skill");
        }
        employee.removeSkill(skillId);
        return skill.getSkill();
    }

    private Employee load(CurrentUser user) {
        return employees.findWithSkills(user.getId()).orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private static void requireTech(CurrentUser user) {
        if (user.getRole() != Role.TECH) {
            throw new AccessDeniedException("Only Tech Engineers have skills");
        }
    }
}
