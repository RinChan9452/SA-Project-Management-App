package com.projectsa.project;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC01 Create Project Profile form. Role rules (Sale/Presale in charge) are checked in ProjectService. */
public class ProjectForm {

    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Project name must be at most 200 characters")
    private String projectName;

    @NotNull(message = "Please choose the Sale in charge")
    private Long saleId;

    @NotNull(message = "Please choose the Presale Engineer in charge")
    private Long presaleId;

    @NotBlank(message = "Customer name is required")
    @Size(max = 200, message = "Customer name must be at most 200 characters")
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Please enter a valid email")
    @Size(max = 255, message = "Customer email is too long")
    private String customerEmail;

    // --- Project Site ---
    @NotBlank(message = "Building number is required")
    @Size(max = 50, message = "At most 50 characters")
    private String buildingNo;

    @Size(max = 50, message = "At most 50 characters")
    private String villageNo;

    @Size(max = 100, message = "At most 100 characters")
    private String alley;

    @Size(max = 100, message = "At most 100 characters")
    private String soi;

    @Size(max = 100, message = "At most 100 characters")
    private String street;

    @NotBlank(message = "Subdistrict is required")
    @Size(max = 100, message = "At most 100 characters")
    private String subdistrict;

    @NotBlank(message = "District is required")
    @Size(max = 100, message = "At most 100 characters")
    private String district;

    @NotBlank(message = "Province is required")
    @Size(max = 100, message = "At most 100 characters")
    private String province;

    @NotBlank(message = "Zip code is required")
    @Size(max = 10, message = "At most 10 characters")
    private String zipcode;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public Long getPresaleId() {
        return presaleId;
    }

    public void setPresaleId(Long presaleId) {
        this.presaleId = presaleId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    /** Trimmed here so {@code @Email} accepts an address with spaces around it. */
    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail == null ? null : customerEmail.trim();
    }

    public String getBuildingNo() {
        return buildingNo;
    }

    public void setBuildingNo(String buildingNo) {
        this.buildingNo = buildingNo;
    }

    public String getVillageNo() {
        return villageNo;
    }

    public void setVillageNo(String villageNo) {
        this.villageNo = villageNo;
    }

    public String getAlley() {
        return alley;
    }

    public void setAlley(String alley) {
        this.alley = alley;
    }

    public String getSoi() {
        return soi;
    }

    public void setSoi(String soi) {
        this.soi = soi;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getSubdistrict() {
        return subdistrict;
    }

    public void setSubdistrict(String subdistrict) {
        this.subdistrict = subdistrict;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getZipcode() {
        return zipcode;
    }

    public void setZipcode(String zipcode) {
        this.zipcode = zipcode;
    }
}
