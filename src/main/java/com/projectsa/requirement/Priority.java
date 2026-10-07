package com.projectsa.requirement;

/** UC06: how urgent a customer requirement is (DB CHECK: LOW, MEDIUM, HIGH). */
public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
