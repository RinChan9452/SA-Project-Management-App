package com.projectsa.project;

import java.util.Map;
import java.util.Set;

/** Project lifecycle (CLAUDE.md §6). All status changes must go through {@link #moveTo}. */
public enum ProjectStatus {
    NEW_PROJECT("New Project"),
    WAITING_FOR_ASSIGN_ENGINEER("Waiting for Engineer Assignment"),
    WORKING("Working"),
    TESTING("Testing"),
    WAITING_FOR_APPROVE("Waiting for Approval"),
    FINISH("Finished");

    private static final Map<ProjectStatus, Set<ProjectStatus>> TRANSITIONS = Map.of(
            NEW_PROJECT, Set.of(WAITING_FOR_ASSIGN_ENGINEER),          // UC02
            WAITING_FOR_ASSIGN_ENGINEER, Set.of(WORKING),               // UC04
            WORKING, Set.of(TESTING),                                   // UC05
            TESTING, Set.of(WAITING_FOR_APPROVE, FINISH),               // UC06 / Complete Project
            WAITING_FOR_APPROVE, Set.of(NEW_PROJECT, FINISH),           // UC07 approve / reject
            FINISH, Set.of());

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canMoveTo(ProjectStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public ProjectStatus moveTo(ProjectStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateException("Project cannot move from " + label + " to " + next.label);
        }
        return next;
    }
}
