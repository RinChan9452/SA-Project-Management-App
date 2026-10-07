package com.projectsa;

import com.projectsa.auth.CurrentUser;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.Project;
import com.projectsa.project.ProjectEngineer;
import com.projectsa.project.ProjectEngineerRepository;
import com.projectsa.project.ProjectForm;
import com.projectsa.project.ProjectRepository;
import com.projectsa.project.ProjectService;
import com.projectsa.project.ProjectStatus;
import com.projectsa.testschedule.TestSchedule;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDateTime;

/** Small helpers shared by tests. */
public final class TestData {

    private TestData() {
    }

    public static Employee employee(EmployeeRepository repo, String name, Role role) {
        Employee e = new Employee(name, name.toLowerCase().replace(' ', '.') + "@example.com", "{noop}x", role);
        if (role == Role.TECH) {
            e.addSkill("CCNA");
        }
        return repo.save(e);
    }

    /** A Tech Engineer with exactly these skills. */
    public static Employee tech(EmployeeRepository repo, String name, String... skills) {
        Employee e = new Employee(name, name.toLowerCase().replace(' ', '.') + "@example.com", "{noop}x", Role.TECH);
        for (String skill : skills) {
            e.addSkill(skill);
        }
        return repo.save(e);
    }

    /** A new project already moved on to WAITING_FOR_ASSIGN_ENGINEER (as if UC02 was done). */
    public static Project projectWaitingForEngineers(ProjectService service, ProjectRepository repo,
                                                     Employee sale, Employee presale) {
        Project p = service.create(projectForm(sale.getId(), presale.getId()), user(sale));
        p.changeStatus(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        return repo.saveAndFlush(p);
    }

    /** A project already in WORKING with this PM in charge and these engineers (as if UC02 + UC04 were done). */
    public static Project projectWorking(ProjectService service, ProjectRepository repo,
                                         ProjectEngineerRepository projectEngineers, Employee sale, Employee presale,
                                         Employee pm, Employee... engineers) {
        Project p = projectWaitingForEngineers(service, repo, sale, presale);
        for (Employee engineer : engineers) {
            projectEngineers.save(new ProjectEngineer(p, engineer, pm));
        }
        p.setPm(pm);
        p.changeStatus(ProjectStatus.WORKING);
        return repo.saveAndFlush(p);
    }

    /**
     * A project already in TESTING whose customer test is at {@code testAt} (as if UC02 + UC04 + UC05 were done).
     * A past {@code testAt} means the customer test is finished, so UC06 is allowed.
     */
    public static Project projectTesting(ProjectService service, ProjectRepository repo,
                                         ProjectEngineerRepository projectEngineers, TestScheduleRepository schedules,
                                         LocalDateTime testAt, Employee sale, Employee presale, Employee pm,
                                         Employee... engineers) {
        Project p = projectWorking(service, repo, projectEngineers, sale, presale, pm, engineers);
        schedules.save(new TestSchedule(p, testAt, "Customer test", engineers.length > 0 ? engineers[0] : pm));
        p.changeStatus(ProjectStatus.TESTING);
        return repo.saveAndFlush(p);
    }

    public static CurrentUser user(Employee e) {
        return new CurrentUser(e);
    }

    public static ProjectForm projectForm(Long saleId, Long presaleId) {
        ProjectForm f = new ProjectForm();
        f.setProjectName("  Network Upgrade ");
        f.setSaleId(saleId);
        f.setPresaleId(presaleId);
        f.setCustomerName("ACME Co.");
        f.setCustomerEmail(" IT@Acme.com ");
        f.setBuildingNo("99/1");
        f.setVillageNo(" ");
        f.setSubdistrict("Lumphini");
        f.setDistrict("Pathum Wan");
        f.setProvince("Bangkok");
        f.setZipcode("10330");
        return f;
    }
}
