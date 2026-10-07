package com.projectsa.project;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.projectForm;
import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.FieldValidationException;
import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** UC01 Create Project Profile. */
@SpringBootTest
@Transactional
class ProjectServiceTest {

    @Autowired
    ProjectService service;
    @Autowired
    ProjectRepository projects;
    @Autowired
    EmployeeRepository employees;

    Employee sale;
    Employee otherSale;
    Employee presale;
    Employee pm;

    @BeforeEach
    void setUp() {
        sale = employee(employees, "Sam Sale", Role.SALE);
        otherSale = employee(employees, "Sue Sale", Role.SALE);
        presale = employee(employees, "Pat Presale", Role.PRESALE);
        pm = employee(employees, "Max Pm", Role.PM);
    }

    @Test
    void saleCreatesProjectInNewProjectState() {
        Project p = service.create(projectForm(sale.getId(), presale.getId()), user(sale));

        Project saved = projects.findById(p.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.NEW_PROJECT);
        assertThat(saved.getProjectName()).isEqualTo("Network Upgrade");
        assertThat(saved.getCustomerEmail()).isEqualTo("it@acme.com");
        assertThat(saved.getSale().getId()).isEqualTo(sale.getId());
        assertThat(saved.getPresale().getId()).isEqualTo(presale.getId());
        assertThat(saved.getCreatedBy().getId()).isEqualTo(sale.getId());
        assertThat(saved.getPm()).isNull();
        assertThat(saved.getEstBudget()).isNull();
        assertThat(saved.getVillageNo()).as("blank optional field stored as NULL").isNull();
        assertThat(saved.getZipcode()).isEqualTo("10330");
    }

    @Test
    void saleCanPutAnotherSaleInCharge() {
        Project p = service.create(projectForm(otherSale.getId(), presale.getId()), user(sale));
        assertThat(p.getSale().getId()).isEqualTo(otherSale.getId());
        assertThat(p.getCreatedBy().getId()).isEqualTo(sale.getId());
    }

    @Test
    void onlySaleCanCreate() {
        for (Employee notSale : new Employee[] {presale, pm}) {
            assertThatThrownBy(() -> service.create(projectForm(sale.getId(), presale.getId()), user(notSale)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(projects.count()).isZero();
    }

    @Test
    void saleInChargeMustHaveSaleRole() {
        assertThatThrownBy(() -> service.create(projectForm(pm.getId(), presale.getId()), user(sale)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "saleId");
    }

    @Test
    void presaleInChargeMustHavePresaleRole() {
        assertThatThrownBy(() -> service.create(projectForm(sale.getId(), pm.getId()), user(sale)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "presaleId");
        assertThatThrownBy(() -> service.create(projectForm(sale.getId(), 999_999L), user(sale)))
                .isInstanceOf(FieldValidationException.class)
                .hasFieldOrPropertyWithValue("field", "presaleId");
    }

    @Test
    void bothPeopleInChargeCheckedAtOnce() {
        assertThatThrownBy(() -> service.create(projectForm(pm.getId(), pm.getId()), user(sale)))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldValidationException.FieldMessage::field)
                        .containsExactly("saleId", "presaleId"));
        assertThat(projects.count()).isZero();
    }

    @Test
    void statusCannotSkipSteps() {
        Project p = service.create(projectForm(sale.getId(), presale.getId()), user(sale));
        assertThatThrownBy(() -> p.changeStatus(ProjectStatus.WORKING)).isInstanceOf(IllegalStateException.class);
    }
}
