package com.projectsa.requirement;

/** Requirement status, separate from the project status (§6). UC06 creates PENDING; UC07 decides. */
public enum RequirementStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    RequirementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
