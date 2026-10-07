package com.projectsa.solution;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/** UC02 form. File rules and "end ≥ start" are checked in SolutionService. */
public class SolutionForm {

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotBlank(message = "Project objective is required")
    @Size(max = 4000, message = "At most 4000 characters")
    private String objective;

    @NotNull(message = "Estimated budget is required")
    @DecimalMin(value = "0", message = "Budget cannot be negative")
    @Digits(integer = 13, fraction = 2, message = "Budget can have at most 13 digits and 2 decimals")
    private BigDecimal estBudget;

    private MultipartFile solutionFile;

    private MultipartFile productRequirementFile;

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public BigDecimal getEstBudget() {
        return estBudget;
    }

    public void setEstBudget(BigDecimal estBudget) {
        this.estBudget = estBudget;
    }

    public MultipartFile getSolutionFile() {
        return solutionFile;
    }

    public void setSolutionFile(MultipartFile solutionFile) {
        this.solutionFile = solutionFile;
    }

    public MultipartFile getProductRequirementFile() {
        return productRequirementFile;
    }

    public void setProductRequirementFile(MultipartFile productRequirementFile) {
        this.productRequirementFile = productRequirementFile;
    }
}
