package com.projectsa.project;

import com.projectsa.auth.CurrentUser;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.requirement.ApprovalService;
import com.projectsa.requirement.Requirement;
import com.projectsa.requirement.RequirementRepository;
import com.projectsa.requirement.RequirementService;
import com.projectsa.solution.SolutionService;
import com.projectsa.testschedule.TestScheduleRepository;
import com.projectsa.testschedule.TestScheduleService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F3 Search &amp; filter project list (UC03 step 1). Every role sees every project; the action buttons of a row
 * use the same rules as the project page, and the services check them again when the action is used.
 */
@Service
public class ProjectListService {

    private final ProjectRepository projects;
    private final ProjectEngineerRepository projectEngineers;
    private final TestScheduleRepository schedules;
    private final RequirementRepository requirements;
    private final EmployeeRepository employees;

    public ProjectListService(ProjectRepository projects, ProjectEngineerRepository projectEngineers,
                              TestScheduleRepository schedules, RequirementRepository requirements,
                              EmployeeRepository employees) {
        this.projects = projects;
        this.projectEngineers = projectEngineers;
        this.schedules = schedules;
        this.requirements = requirements;
        this.employees = employees;
    }

    /**
     * One project in the list with the actions this user may take on it right now.
     *
     * @param reviewRequirementId the pending requirement the Sale in charge can review (UC07), or null
     * @param canComplete         Complete Project is possible; the button opens the project page to confirm there
     */
    public record ProjectRow(Project project, boolean canStoreSolution, boolean canAssignEngineers,
                             boolean canSetTestDate, boolean canAddRequirement, Long reviewRequirementId,
                             boolean canComplete) {

        public boolean hasAction() {
            return canStoreSolution || canAssignEngineers || canSetTestDate || canAddRequirement
                    || reviewRequirementId != null || canComplete;
        }
    }

    /** The requested page of matching projects, newest first. A page past the end shows the last page. */
    @Transactional(readOnly = true)
    public Page<ProjectRow> search(ProjectSearch search, CurrentUser user) {
        Page<Project> page = find(search, user, search.page() - 1);
        if (page.getTotalPages() > 0 && search.page() > page.getTotalPages()) {
            page = find(search, user, page.getTotalPages() - 1);
        }
        List<Long> ids = page.getContent().stream().map(Project::getId).toList();
        if (ids.isEmpty()) {
            return page.map(p -> null);
        }
        Set<Long> assigned = user.getRole() == Role.TECH
                ? projectEngineers.findAssignedProjectIds(user.getId(), ids) : Set.of();
        Map<Long, LocalDateTime> latestTest = new HashMap<>();
        for (Object[] row : schedules.findLatestTestAts(ids)) {
            latestTest.put((Long) row[0], (LocalDateTime) row[1]);
        }
        Map<Long, Long> toReview = new HashMap<>();
        if (user.getRole() == Role.SALE) {
            Map<Long, Project> byId = new HashMap<>();
            page.getContent().forEach(p -> byId.put(p.getId(), p));
            for (Requirement r : requirements.findPendingForProjects(ids)) {
                Project p = byId.get(r.getProject().getId());
                if (ApprovalService.canDecide(r, p, user)) {
                    toReview.putIfAbsent(p.getId(), r.getId());
                }
            }
        }
        return page.map(p -> new ProjectRow(p,
                SolutionService.canStoreSolution(p, user),
                AssignEngineerService.canAssign(p, user),
                TestScheduleService.canSetTestDate(p, assigned.contains(p.getId()), user),
                RequirementService.canAddRequirement(p, latestTest.get(p.getId()), user),
                toReview.get(p.getId()),
                CompleteProjectService.canComplete(p, latestTest.get(p.getId()), user)));
    }

    /** Choices for the "Sale in charge" filter. */
    @Transactional(readOnly = true)
    public List<Employee> sales() {
        return employees.findByRoleOrderByName(Role.SALE);
    }

    private Page<Project> find(ProjectSearch search, CurrentUser user, int pageIndex) {
        return projects.search(search.status(), search.saleId(), search.likePattern(), search.idValue(),
                search.mine() ? user.getId() : null, PageRequest.of(pageIndex, ProjectSearch.PAGE_SIZE));
    }
}
