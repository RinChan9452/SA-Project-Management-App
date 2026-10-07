package com.projectsa.employee;

/** The one role every employee has (CLAUDE.md §2). */
public enum Role {
    SALE("Sale"),
    PRESALE("Presale Engineer"),
    PM("Project Manager"),
    TECH("Tech Engineer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
