package com.projectsa.project;

import com.projectsa.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The Project Profile (CLAUDE.md §5). Status only changes through {@link #changeStatus}. */
@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Employee sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presale_id")
    private Employee presale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pm_id")
    private Employee pm;

    @Column(length = 4000)
    private String objective;

    @Column(name = "est_budget", precision = 15, scale = 2)
    private BigDecimal estBudget;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    // --- Project Site address ---
    @Column(name = "building_no", nullable = false, length = 50)
    private String buildingNo;

    @Column(name = "village_no", length = 50)
    private String villageNo;

    @Column(length = 100)
    private String alley;

    @Column(length = 100)
    private String soi;

    @Column(length = 100)
    private String street;

    @Column(nullable = false, length = 100)
    private String subdistrict;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 10)
    private String zipcode;

    protected Project() {
    }

    /** UC01: a new project always starts as {@link ProjectStatus#NEW_PROJECT}. */
    public Project(String projectName, String customerName, String customerEmail,
                   Employee sale, Employee presale, Employee createdBy) {
        this.projectName = projectName;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.sale = sale;
        this.presale = presale;
        this.createdBy = createdBy;
        this.status = ProjectStatus.NEW_PROJECT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** The only way to change status; throws if the §6 state machine does not allow it. */
    public void changeStatus(ProjectStatus next) {
        this.status = status.moveTo(next);
    }

    /** UC02: solution details from the Presale in charge (status is changed separately). */
    public void setSolution(LocalDate startDate, LocalDate endDate, String objective, BigDecimal estBudget) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.objective = objective;
        this.estBudget = estBudget;
    }

    /** UC04: the PM who assigns the engineers becomes the PM in charge. */
    public void setPm(Employee pm) {
        this.pm = pm;
    }

    public void setSite(String buildingNo, String villageNo, String alley, String soi, String street,
                        String subdistrict, String district, String province, String zipcode) {
        this.buildingNo = buildingNo;
        this.villageNo = villageNo;
        this.alley = alley;
        this.soi = soi;
        this.street = street;
        this.subdistrict = subdistrict;
        this.district = district;
        this.province = province;
        this.zipcode = zipcode;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Employee getSale() {
        return sale;
    }

    public Employee getPresale() {
        return presale;
    }

    public Employee getPm() {
        return pm;
    }

    public String getObjective() {
        return objective;
    }

    public BigDecimal getEstBudget() {
        return estBudget;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getBuildingNo() {
        return buildingNo;
    }

    public String getVillageNo() {
        return villageNo;
    }

    public String getAlley() {
        return alley;
    }

    public String getSoi() {
        return soi;
    }

    public String getStreet() {
        return street;
    }

    public String getSubdistrict() {
        return subdistrict;
    }

    public String getDistrict() {
        return district;
    }

    public String getProvince() {
        return province;
    }

    public String getZipcode() {
        return zipcode;
    }
}
