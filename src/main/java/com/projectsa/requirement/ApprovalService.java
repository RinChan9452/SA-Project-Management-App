package com.projectsa.requirement;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.ActionNotAllowedException;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.notification.NotificationService;
import com.projectsa.notification.NotificationType;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineer;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectStatus;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC07 Approve New Project Requirement — only the project's Sale in charge, only while the requirement is PENDING.
 * Approve sends the project back to NEW_PROJECT (Presale updates the solution); reject finishes it.
 */
@Service
public class ApprovalService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final String CHANGED = "This requirement was just changed by someone else. Please open it again.";

    private final RequirementRepository requirements;
    private final ProjectRepository projects;
    private final ProjectEngineerRepository projectEngineers;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public ApprovalService(RequirementRepository requirements, ProjectRepository projects,
                           ProjectEngineerRepository projectEngineers, EmployeeRepository employees,
                           NotificationService notifications) {
        this.requirements = requirements;
        this.projects = projects;
        this.projectEngineers = projectEngineers;
        this.employees = employees;
        this.notifications = notifications;
    }

    /** UC07 step 1 / SALE dashboard: pending requirements of my projects, oldest first. */
    @Transactional(readOnly = true)
    public List<Requirement> pendingForSale(CurrentUser user) {
        return requirements.findPendingForSale(user.getId());
    }

    /** UC07 step 2: loads the requirement after checking role, Sale in charge and that it is still PENDING. */
    @Transactional(readOnly = true)
    public Requirement requirementForDecision(Long requirementId, CurrentUser user) {
        Requirement requirement = requirements.findForDecision(requirementId)
                .orElseThrow(() -> new NotFoundException("Requirement not found"));
        checkAllowed(requirement, user);
        return requirement;
    }

    /**
     * UC07 steps 4–6: saves the decision, moves the project (approve → NEW_PROJECT, reject → FINISH) and notifies the
     * PM in charge, plus the Presale in charge (who updates the solution) and the assigned engineers when approved.
     *
     * @throws FieldValidationException  on {@code decision} / {@code rejectReason} if a business rule fails
     * @throws ActionNotAllowedException if the requirement is no longer PENDING or was changed since the page was opened
     */
    @Transactional
    public Requirement decide(Long requirementId, ApprovalForm form, CurrentUser user) {
        Requirement requirement = requirementForDecision(requirementId, user);
        check(form);
        if (form.getVersion() == null || form.getVersion() != requirement.getVersion()) {
            throw new ActionNotAllowedException(CHANGED);
        }

        Project project = requirement.getProject();
        boolean approved = form.getDecision() == RequirementStatus.APPROVED;
        requirement.decide(form.getDecision(), approved ? null : form.getRejectReason().trim(),
                employees.getReferenceById(user.getId()));
        project.changeStatus(approved ? ProjectStatus.NEW_PROJECT : ProjectStatus.FINISH);
        try {
            requirements.saveAndFlush(requirement);
            projects.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ActionNotAllowedException(CHANGED);
        }

        String about = "requirement \"" + requirement.getTitle() + "\" for project \"" + project.getProjectName()
                + "\" (ID " + project.getId() + ")";
        if (approved) {
            String message = "The new " + about + " was approved by " + user.getName()
                    + ". The project is back to New Project so the Presale Engineer can update the solution.";
            if (project.getPm() != null) {
                notifications.notify(project.getPm(), project, NotificationType.REQUIREMENT_APPROVED, message);
            }
            notifications.notify(project.getPresale(), project, NotificationType.REQUIREMENT_APPROVED,
                    "The new " + about + " was approved by " + user.getName()
                            + ". The project is back to New Project: please update the solution.");
            for (ProjectEngineer pe : projectEngineers.findForProject(project.getId())) {
                notifications.notify(pe.getEmployee(), project, NotificationType.REQUIREMENT_APPROVED, message);
            }
        } else if (project.getPm() != null) {
            // Reason last: a very long one is cut to fit the message column, the rest stays readable
            notifications.notify(project.getPm(), project, NotificationType.REQUIREMENT_REJECTED,
                    "The new " + about + " was rejected by " + user.getName()
                            + ". The project is now Finished. Reason: " + requirement.getRejectReason());
        }
        return requirement;
    }

    /**
     * UC07 business rules only, so the form can show them together with its own field errors.
     *
     * @throws FieldValidationException with every rule that fails
     */
    @Transactional(readOnly = true)
    public void validate(Long requirementId, ApprovalForm form, CurrentUser user) {
        requirementForDecision(requirementId, user);
        check(form);
    }

    /** True when the user may approve or reject this requirement now (for the buttons on UC03). */
    public static boolean canDecide(Requirement requirement, Project project, CurrentUser user) {
        return user.getRole() == Role.SALE
                && project.getSale().getId().equals(user.getId())
                && requirement.getStatus() == RequirementStatus.PENDING
                && project.getStatus() == ProjectStatus.WAITING_FOR_APPROVE;
    }

    private void check(ApprovalForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        if (form.getDecision() == RequirementStatus.PENDING) {
            errors.add(new FieldMessage("decision", "Please choose Approve or Reject"));
        }
        if (form.getDecision() == RequirementStatus.REJECTED
                && (form.getRejectReason() == null || form.getRejectReason().isBlank())) {
            errors.add(new FieldMessage("rejectReason", "Please give the reason for rejecting"));
        }
        FieldValidationException.throwIfAny(errors);
    }

    private void checkAllowed(Requirement requirement, CurrentUser user) {
        if (user == null || user.getRole() != Role.SALE) {
            throw new AccessDeniedException("Only a Sale can approve or reject a requirement");
        }
        Project project = requirement.getProject();
        if (!project.getSale().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the Sale in charge of this project can approve or reject its requirements");
        }
        if (requirement.getStatus() != RequirementStatus.PENDING) {
            Employee by = requirement.getDecidedBy();
            throw new ActionNotAllowedException("The requirement \"" + requirement.getTitle() + "\" was already "
                    + requirement.getStatus().getLabel().toLowerCase(Locale.ENGLISH)
                    + (by == null ? "" : " by " + by.getName() + " on " + requirement.getDecidedAt().format(WHEN)) + ".");
        }
        if (project.getStatus() != ProjectStatus.WAITING_FOR_APPROVE) {
            throw new ActionNotAllowedException("A requirement can only be decided while the project is \""
                    + ProjectStatus.WAITING_FOR_APPROVE.getLabel() + "\". This project is \""
                    + project.getStatus().getLabel() + "\".");
        }
    }
}
