package com.projectsa.requirement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC07 form. "Reason required when rejecting" is checked in ApprovalService. */
public class ApprovalForm {

    @NotNull(message = "Please choose Approve or Reject")
    private RequirementStatus decision;

    @Size(max = 1000, message = "At most 1000 characters")
    private String rejectReason;

    /** Version of the requirement when the page was opened; a different one means it changed meanwhile. */
    private Long version;

    public RequirementStatus getDecision() {
        return decision;
    }

    public void setDecision(RequirementStatus decision) {
        this.decision = decision;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
