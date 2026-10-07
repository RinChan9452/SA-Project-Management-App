package com.projectsa.notification;

/** Why a notification was sent (CLAUDE.md F2 sources). Label and Bootstrap icon are for the F2 list. */
public enum NotificationType {
    /** UC04: an engineer was assigned to a project. */
    ASSIGNED("Assigned to project", "bi-person-check"),
    /** UC05: a customer test date was set (sent right away). */
    TEST_SCHEDULED("Test scheduled", "bi-calendar-event"),
    /** UC05: reminder, shown from 1 day before the test. */
    TEST_REMINDER("Test reminder", "bi-alarm"),
    /** UC06: a new customer requirement waits for the Sale in charge to approve or reject it. */
    REQUIREMENT_PENDING("New requirement", "bi-exclamation-circle"),
    /** UC07: the Sale in charge approved a requirement (to the PM in charge, Presale in charge and assigned engineers). */
    REQUIREMENT_APPROVED("Requirement approved", "bi-check-circle"),
    /** UC07: the Sale in charge rejected a requirement (to the PM in charge). */
    REQUIREMENT_REJECTED("Requirement rejected", "bi-x-circle"),
    /** Complete Project: the PM in charge finished the project (to Sale, Presale and assigned engineers). */
    PROJECT_COMPLETED("Project completed", "bi-flag");

    private final String label;
    private final String icon;

    NotificationType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
