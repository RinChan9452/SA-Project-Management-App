package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.common.FieldValidationException;
import com.projectsa.common.FieldValidationException.FieldMessage;
import com.projectsa.common.NotFoundException;
import com.projectsa.employee.Emails;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final EmployeeRepository employees;
    private final ProjectEngineerRepository projectEngineers;

    public ProjectService(ProjectRepository projects, EmployeeRepository employees,
                          ProjectEngineerRepository projectEngineers) {
        this.projects = projects;
        this.employees = employees;
        this.projectEngineers = projectEngineers;
    }

    /**
     * UC01 Create Project Profile. Only a Sale may create; Sale/Presale in charge must have those roles.
     *
     * @throws AccessDeniedException    if the current user is not a Sale
     * @throws FieldValidationException if a person in charge is missing or has the wrong role
     */
    @Transactional
    public Project create(ProjectForm form, CurrentUser user) {
        requireRole(user, Role.SALE);
        PeopleInCharge people = peopleInCharge(form);
        Employee sale = people.sale();
        Employee presale = people.presale();
        Employee creator = employees.getReferenceById(user.getId());

        Project project = new Project(form.getProjectName().trim(), form.getCustomerName().trim(),
                Emails.normalize(form.getCustomerEmail()), sale, presale, creator);
        project.setSite(trim(form.getBuildingNo()), blankToNull(form.getVillageNo()), blankToNull(form.getAlley()),
                blankToNull(form.getSoi()), blankToNull(form.getStreet()), trim(form.getSubdistrict()),
                trim(form.getDistrict()), trim(form.getProvince()), trim(form.getZipcode()));
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public List<Project> projectsOfSale(Long saleId) {
        return projects.findBySaleIdOrderByCreatedAtDesc(saleId);
    }

    @Transactional(readOnly = true)
    public List<Project> projectsOfPresale(Long presaleId) {
        return projects.findByPresaleIdOrderByCreatedAtDesc(presaleId);
    }

    @Transactional(readOnly = true)
    public List<Project> projectsOfPm(Long pmId) {
        return projects.findByPmIdOrderByCreatedAtDesc(pmId);
    }

    @Transactional(readOnly = true)
    public List<Project> projectsOfEngineer(Long engineerId) {
        return projectEngineers.findProjectsOfEngineer(engineerId);
    }

    /** UC04 step 1: projects this PM may assign engineers to (no PM in charge yet, or this PM is in charge). */
    @Transactional(readOnly = true)
    public List<Project> projectsNeedingEngineers(CurrentUser user) {
        return projects.findByStatusOrderByCreatedAtAsc(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER).stream()
                .filter(p -> AssignEngineerService.canAssign(p, user))
                .toList();
    }

    /** UC03: assigned engineers with their skills. */
    @Transactional(readOnly = true)
    public List<ProjectEngineer> engineersOf(Long projectId) {
        return projectEngineers.findForProject(projectId);
    }

    /** UC03 detail (read-only). */
    @Transactional(readOnly = true)
    public Project detail(Long id) {
        return projects.findDetailById(id).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    /**
     * UC01 business rules only, so the form can show them together with its own field errors.
     *
     * @throws FieldValidationException if a person in charge is missing or has the wrong role
     */
    @Transactional(readOnly = true)
    public void validate(ProjectForm form, CurrentUser user) {
        requireRole(user, Role.SALE);
        peopleInCharge(form);
    }

    private record PeopleInCharge(Employee sale, Employee presale) {
    }

    /** Checks both people in charge and reports every problem at once. */
    private PeopleInCharge peopleInCharge(ProjectForm form) {
        List<FieldMessage> errors = new ArrayList<>();
        Employee sale = employeeWithRole(form.getSaleId(), Role.SALE, "saleId",
                "Sale in charge must be an employee with the Sale role", errors);
        Employee presale = employeeWithRole(form.getPresaleId(), Role.PRESALE, "presaleId",
                "Presale in charge must be an employee with the Presale Engineer role", errors);
        FieldValidationException.throwIfAny(errors);
        return new PeopleInCharge(sale, presale);
    }

    private Employee employeeWithRole(Long id, Role role, String field, String message, List<FieldMessage> errors) {
        Employee employee = id == null ? null : employees.findById(id).filter(e -> e.getRole() == role).orElse(null);
        if (employee == null) {
            errors.add(new FieldMessage(field, message));
        }
        return employee;
    }

    static void requireRole(CurrentUser user, Role role) {
        if (user == null || user.getRole() != role) {
            throw new AccessDeniedException("Only " + role.getLabel() + " can do this");
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
