package com.projectsa.project;

import static com.projectsa.project.ProjectStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProjectStatusTest {

    @Test
    void happyPathReachesFinish() {
        ProjectStatus s = NEW_PROJECT.moveTo(WAITING_FOR_ASSIGN_ENGINEER)
                .moveTo(WORKING)
                .moveTo(TESTING)
                .moveTo(FINISH);
        assertEquals(FINISH, s);
    }

    @Test
    void newRequirementLoop() {
        assertEquals(WAITING_FOR_APPROVE, TESTING.moveTo(WAITING_FOR_APPROVE));
        assertEquals(NEW_PROJECT, WAITING_FOR_APPROVE.moveTo(NEW_PROJECT));  // approved
        assertEquals(FINISH, WAITING_FOR_APPROVE.moveTo(FINISH));            // rejected
    }

    @Test
    void cannotSkipSteps() {
        assertThrows(IllegalStateException.class, () -> NEW_PROJECT.moveTo(WORKING));
        assertThrows(IllegalStateException.class, () -> WAITING_FOR_ASSIGN_ENGINEER.moveTo(TESTING));
        assertThrows(IllegalStateException.class, () -> WORKING.moveTo(WAITING_FOR_APPROVE));
        assertThrows(IllegalStateException.class, () -> WORKING.moveTo(FINISH));
    }

    @Test
    void finishIsTerminal() {
        for (ProjectStatus next : values()) {
            assertFalse(FINISH.canMoveTo(next), "FINISH -> " + next);
        }
    }
}
