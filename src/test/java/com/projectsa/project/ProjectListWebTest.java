package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectForm;
import static com.projectsa.TestData.projectTesting;
import static com.projectsa.TestData.projectWaitingForEngineers;
import static com.projectsa.TestData.projectWorking;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import com.projectsa.project.ProjectListService.ProjectRow;
import com.projectsa.requirement.Priority;
import com.projectsa.requirement.Requirement;
import com.projectsa.requirement.RequirementRepository;
import com.projectsa.testschedule.TestScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** F3 Search &amp; filter project list over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectListWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;
    @Autowired
    ProjectEngineerRepository projectEngineers;
    @Autowired
    TestScheduleRepository schedules;
    @Autowired
    RequirementRepository requirements;

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;
    Employee tech;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        tech = employee(employees, "Tia Tech", Role.TECH);
    }

    Project create(String name, String customer, Employee saleInCharge) {
        ProjectForm f = projectForm(saleInCharge.getId(), presale.getId());
        f.setProjectName(name);
        f.setCustomerName(customer);
        return projectService.create(f, user(saleInCharge));
    }

    @SuppressWarnings("unchecked")
    Page<ProjectRow> rows(Employee as, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(user(as)))).andExpect(status().isOk()).andReturn();
        return (Page<ProjectRow>) result.getModelAndView().getModel().get("rows");
    }

    List<String> names(Page<ProjectRow> page) {
        return page.getContent().stream().map(r -> r.project().getProjectName()).toList();
    }

    @Test
    void everyRoleSeesEveryProjectNewestFirst() throws Exception {
        create("First", "Acme", sale);
        create("Second", "Beta", otherSale);
        for (Employee e : List.of(sale, presale, pm, tech)) {
            assertThat(names(rows(e, get("/projects")))).containsExactly("Second", "First");
        }
    }

    @Test
    void searchMatchesNameOrCustomerPartialAndIgnoresCase() throws Exception {
        create("Core Switch Upgrade", "Acme Co.", sale);
        create("CCTV Install", "Big SWITCHES Ltd", sale);
        create("Wifi Rollout", "Gamma", sale);

        assertThat(names(rows(tech, get("/projects").param("q", "switch"))))
                .containsExactlyInAnyOrder("Core Switch Upgrade", "CCTV Install");
        assertThat(names(rows(tech, get("/projects").param("q", "ACME")))).containsExactly("Core Switch Upgrade");
        assertThat(names(rows(tech, get("/projects").param("q", "nothing like this")))).isEmpty();
    }

    @Test
    void searchByProjectId() throws Exception {
        Project a = create("Alpha", "Acme", sale);
        create("Beta", "Beta", sale);
        assertThat(names(rows(pm, get("/projects").param("q", a.getId().toString())))).containsExactly("Alpha");
        assertThat(names(rows(pm, get("/projects").param("q", "#" + a.getId())))).containsExactly("Alpha");
    }

    @Test
    void wildcardsInSearchTextAreTakenLiterally() throws Exception {
        create("Discount 50% plan", "Acme", sale);
        create("Discount 500 plan", "Acme", sale);
        create("snake_case", "Acme", sale);
        create("snakeXcase", "Acme", sale);
        assertThat(names(rows(pm, get("/projects").param("q", "50%")))).containsExactly("Discount 50% plan");
        assertThat(names(rows(pm, get("/projects").param("q", "e_c")))).containsExactly("snake_case");
    }

    @Test
    void filtersStatusAndSaleCombineWithAnd() throws Exception {
        create("A new", "Acme", sale);
        projectWaitingForEngineers(projectService, projects, sale, presale);          // "Network Upgrade", waiting
        create("C new other", "Acme", otherSale);

        assertThat(names(rows(pm, get("/projects").param("status", "NEW_PROJECT"))))
                .containsExactlyInAnyOrder("A new", "C new other");
        assertThat(names(rows(pm, get("/projects").param("saleId", sale.getId().toString()))))
                .containsExactlyInAnyOrder("A new", "Network Upgrade");
        assertThat(names(rows(pm, get("/projects").param("status", "NEW_PROJECT")
                .param("saleId", sale.getId().toString()).param("q", "a new")))).containsExactly("A new");
    }

    @Test
    void onlyMyProjectsMeansInChargeOrAssigned() throws Exception {
        Employee otherPresale = employee(employees, "Pia Presale", Role.PRESALE);
        Employee otherTech = employee(employees, "Ted Tech", Role.TECH);
        create("Sue's", "Acme", otherSale);
        Project working = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, tech);

        assertThat(names(rows(sale, get("/projects").param("mine", "true")))).containsExactly(working.getProjectName());
        assertThat(names(rows(otherSale, get("/projects").param("mine", "true")))).containsExactly("Sue's");
        assertThat(names(rows(presale, get("/projects").param("mine", "true")))).hasSize(2);
        assertThat(names(rows(otherPresale, get("/projects").param("mine", "true")))).isEmpty();
        assertThat(names(rows(pm, get("/projects").param("mine", "true")))).containsExactly(working.getProjectName());
        assertThat(names(rows(tech, get("/projects").param("mine", "true")))).containsExactly(working.getProjectName());
        assertThat(names(rows(otherTech, get("/projects").param("mine", "true")))).isEmpty();
    }

    @Test
    void twentyPerPageAndPagingKeepsFilters() throws Exception {
        for (int i = 1; i <= 25; i++) {
            create("Match " + i, "Acme", sale);
        }
        create("Other", "Beta", sale);

        Page<ProjectRow> first = rows(pm, get("/projects").param("q", "match"));
        assertThat(first.getContent()).hasSize(20);
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(names(first).getFirst()).isEqualTo("Match 25");

        Page<ProjectRow> second = rows(pm, get("/projects").param("q", "match").param("page", "2"));
        assertThat(names(second)).hasSize(5).last().isEqualTo("Match 1");

        // A page past the end shows the last page
        assertThat(rows(pm, get("/projects").param("q", "match").param("page", "9")).getNumber()).isEqualTo(1);
        // ... also when the page is so large that its row offset would not fit in an int
        assertThat(rows(pm, get("/projects").param("q", "match").param("page", "2147483647")).getNumber()).isEqualTo(1);

        mvc.perform(get("/projects").param("q", "match").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("href=\"/projects?q=match&amp;page=2\"")))
                .andExpect(content().string(Matchers.containsString("Showing 1–20 of 25 projects")));
    }

    @Test
    void badParametersShowTheWholeListInsteadOfAnError() throws Exception {
        create("Alpha", "Acme", sale);
        assertThat(names(rows(pm, get("/projects").param("status", "NOPE").param("saleId", "x")
                .param("page", "abc").param("mine", "yes")))).containsExactly("Alpha");
    }

    @Test
    void emptyResultSaysSo() throws Exception {
        mvc.perform(get("/projects").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("There are no projects yet")));
        create("Alpha", "Acme", sale);
        mvc.perform(get("/projects").param("q", "zzz").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("No project matches your search")));
    }

    @Test
    void rowButtonsFollowRoleAndState() throws Exception {
        Project fresh = create("Fresh", "Acme", sale);
        Project waiting = projectWaitingForEngineers(projectService, projects, sale, presale);
        Project working = projectWorking(projectService, projects, projectEngineers, sale, presale, pm, tech);
        Project tested = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusDays(8), sale, presale, pm, tech);
        Project pending = projectTesting(projectService, projects, projectEngineers, schedules,
                LocalDateTime.now().minusDays(1), sale, presale, pm, tech);
        Requirement r = requirements.save(new Requirement(pending, "More", "Detail", Priority.HIGH,
                LocalDate.now(), null, pm));
        pending.changeStatus(ProjectStatus.WAITING_FOR_APPROVE);
        projects.saveAndFlush(pending);

        ProjectRow forPresale = row(presale, fresh);
        assertThat(forPresale.canStoreSolution()).isTrue();
        assertThat(row(presale, waiting).hasAction()).isFalse();
        assertThat(row(employee(employees, "Pia Presale", Role.PRESALE), fresh).hasAction()).isFalse();

        assertThat(row(pm, waiting).canAssignEngineers()).isTrue();
        assertThat(row(pm, tested).canAddRequirement()).isTrue();
        assertThat(row(pm, tested).canComplete()).isTrue();
        assertThat(row(pm, fresh).hasAction()).isFalse();

        assertThat(row(tech, working).canSetTestDate()).isTrue();
        assertThat(row(employee(employees, "Ted Tech", Role.TECH), working).hasAction()).isFalse();

        assertThat(row(sale, pending).reviewRequirementId()).isEqualTo(r.getId());
        assertThat(row(otherSale, pending).hasAction()).isFalse();
        assertThat(row(sale, fresh).hasAction()).isFalse();

        mvc.perform(get("/projects").with(user(user(sale))))
                .andExpect(content().string(Matchers.containsString("/requirements/" + r.getId() + "/decision")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/solution"))));
    }

    ProjectRow row(Employee as, Project project) throws Exception {
        return rows(as, get("/projects").param("q", project.getId().toString())).getContent().stream()
                .filter(r -> r.project().getId().equals(project.getId())).findFirst().orElseThrow();
    }
}
