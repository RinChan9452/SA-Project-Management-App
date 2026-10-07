package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** UC01 over HTTP: access per role, validation messages, redirect + dashboard list. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ProjectRepository projects;

    Employee sale;
    Employee presale;
    Employee pm;
    Employee tech;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
        tech = employee(employees, "Tia Tech", Role.TECH);
    }

    MockHttpServletRequestBuilder validPost() {
        return postWith(Map.of());
    }

    MockHttpServletRequestBuilder postWithPresale(Employee presaleInCharge) {
        return postWith(Map.of("presaleId", presaleInCharge.getId().toString()));
    }

    /** A valid form with some values replaced (MockMvc's param() would add a second value instead). */
    MockHttpServletRequestBuilder postWith(Map<String, String> overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("projectName", "Network Upgrade");
        params.put("saleId", sale.getId().toString());
        params.put("presaleId", presale.getId().toString());
        params.put("customerName", "ACME Co.");
        params.put("customerEmail", "it@acme.com");
        params.put("buildingNo", "99/1");
        params.put("subdistrict", "Lumphini");
        params.put("district", "Pathum Wan");
        params.put("province", "Bangkok");
        params.put("zipcode", "10330");
        params.putAll(overrides);
        MockHttpServletRequestBuilder request = post("/projects").with(csrf());
        params.forEach((name, value) -> request.param(name, value));
        return request;
    }

    @Test
    void saleSeesFormWithPeopleToChoose() throws Exception {
        mvc.perform(get("/projects/new").with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Pat Presale")))
                .andExpect(model().attribute("form", Matchers.hasProperty("saleId", Matchers.is(sale.getId()))));
    }

    @Test
    void otherRolesGetAccessDenied() throws Exception {
        for (Employee e : new Employee[] {presale, pm, tech}) {
            mvc.perform(get("/projects/new").with(user(user(e)))).andExpect(status().isForbidden());
            mvc.perform(validPost().with(user(user(e)))).andExpect(status().isForbidden());
        }
        assertThat(projects.count()).isZero();
    }

    @Test
    void notLoggedInIsSentToLogin() throws Exception {
        mvc.perform(get("/projects/new")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void validFormCreatesProjectAndShowsItOnDashboard() throws Exception {
        mvc.perform(validPost().with(user(user(sale))))
                .andExpect(redirectedUrlPattern("/projects/*"))
                .andExpect(flash().attribute("success", Matchers.containsString("Network Upgrade")));
        assertThat(projects.count()).isEqualTo(1);

        mvc.perform(get("/dashboard").with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Network Upgrade")))
                .andExpect(content().string(Matchers.containsString("New Project")));
    }

    @Test
    void missingFieldsShowErrorsAndSaveNothing() throws Exception {
        mvc.perform(post("/projects").with(csrf()).with(user(user(sale)))
                        .param("customerEmail", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "projectName", "saleId", "presaleId",
                        "customerName", "customerEmail", "buildingNo", "subdistrict", "district", "province", "zipcode"));
        assertThat(projects.count()).isZero();
    }

    @Test
    void bothPeopleInChargeWrongShowsBothErrors() throws Exception {
        mvc.perform(postWith(Map.of("saleId", tech.getId().toString(), "presaleId", pm.getId().toString()))
                        .with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "saleId", "presaleId"));
        assertThat(projects.count()).isZero();
    }

    @Test
    void roleErrorsShowTogetherWithMissingFields() throws Exception {
        mvc.perform(postWith(Map.of("projectName", "", "presaleId", pm.getId().toString())).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "projectName", "presaleId"));
    }

    @Test
    void unreadableIdShowsPlainMessage() throws Exception {
        mvc.perform(postWith(Map.of("saleId", "abc")).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "saleId"))
                .andExpect(content().string(Matchers.containsString("Please choose the Sale in charge")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
        assertThat(projects.count()).isZero();
    }

    @Test
    void customerEmailWithSpacesIsAccepted() throws Exception {
        mvc.perform(postWith(Map.of("customerEmail", "  IT@Acme.com ")).with(user(user(sale))))
                .andExpect(redirectedUrlPattern("/projects/*"));
        assertThat(projects.findAll()).singleElement()
                .extracting(Project::getCustomerEmail).isEqualTo("it@acme.com");
    }

    @Test
    void wrongRoleForPresaleInChargeShowsFieldError() throws Exception {
        mvc.perform(postWithPresale(pm).with(user(user(sale))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "presaleId"));
        assertThat(projects.count()).isZero();
    }
}
