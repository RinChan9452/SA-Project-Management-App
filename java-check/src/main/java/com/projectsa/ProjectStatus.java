package com.projectsa;

import java.util.Map;
import java.util.Set;

/** Project lifecycle from the State Diagram (CLAUDE.md §6). */
public enum ProjectStatus {
    NEW_PROJECT,
    WAITING_FOR_ASSIGN_ENGINEER,
    WORKING,
    TESTING,
    WAITING_FOR_APPROVE,
    FINISH;

    private static final Map<ProjectStatus, Set<ProjectStatus>> TRANSITIONS = Map.of(
            NEW_PROJECT, Set.of(WAITING_FOR_ASSIGN_ENGINEER),
            WAITING_FOR_ASSIGN_ENGINEER, Set.of(WORKING),
            WORKING, Set.of(TESTING),
            TESTING, Set.of(WAITING_FOR_APPROVE, FINISH),
            WAITING_FOR_APPROVE, Set.of(NEW_PROJECT, FINISH),
            FINISH, Set.of());

    public boolean canMoveTo(ProjectStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public ProjectStatus moveTo(ProjectStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateException("Illegal transition " + this + " -> " + next);
        }
        return next;
    }
}
