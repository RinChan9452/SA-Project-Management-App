package com.projectsa;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProjectStatusTest {

    @Test
    void happyPathReachesFinish() {
        ProjectStatus s = ProjectStatus.NEW_PROJECT
                .moveTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER)
                .moveTo(ProjectStatus.WORKING)
                .moveTo(ProjectStatus.TESTING)
                .moveTo(ProjectStatus.FINISH);
        assertEquals(ProjectStatus.FINISH, s);
    }

    @Test
    void approvedRequirementGoesBackToNewProject() {
        assertTrue(ProjectStatus.WAITING_FOR_APPROVE.canMoveTo(ProjectStatus.NEW_PROJECT));
    }

    @Test
    void cannotSkipAssigningEngineers() {
        assertThrows(IllegalStateException.class,
                () -> ProjectStatus.NEW_PROJECT.moveTo(ProjectStatus.WORKING));
    }

    @Test
    void finishIsTerminal() {
        for (ProjectStatus next : ProjectStatus.values()) {
            assertFalse(ProjectStatus.FINISH.canMoveTo(next));
        }
    }
}
