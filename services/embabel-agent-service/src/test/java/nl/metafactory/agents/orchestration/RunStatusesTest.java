package nl.metafactory.agents.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RunStatuses}.
 */
class RunStatusesTest {

    @Test
    void constantsHaveExpectedLiteralValues() {
        assertEquals("COMPLETED", RunStatuses.COMPLETED);
        assertEquals("FAILED", RunStatuses.FAILED);
        assertEquals("CANCELLED", RunStatuses.CANCELLED);
        assertEquals("DENIED", RunStatuses.DENIED);
        assertEquals("RUN_STATE_LOST", RunStatuses.RUN_STATE_LOST);
        assertEquals("TIMED_OUT", RunStatuses.TIMED_OUT);
        assertEquals("AWAITING_CHILD_WORKFLOW", RunStatuses.AWAITING_CHILD_WORKFLOW);
        assertEquals("BLOCKED", RunStatuses.BLOCKED);
    }

    @Test
    void isTerminalReturnsTrueForAllTerminalStatuses() {
        assertTrue(RunStatuses.isTerminal(RunStatuses.COMPLETED));
        assertTrue(RunStatuses.isTerminal(RunStatuses.FAILED));
        assertTrue(RunStatuses.isTerminal(RunStatuses.CANCELLED));
        assertTrue(RunStatuses.isTerminal(RunStatuses.DENIED));
        assertTrue(RunStatuses.isTerminal(RunStatuses.RUN_STATE_LOST));
        assertTrue(RunStatuses.isTerminal(RunStatuses.TIMED_OUT));
        assertTrue(RunStatuses.isTerminal(RunStatuses.BLOCKED));
    }

    @Test
    void isTerminalReturnsFalseForNonTerminalStatuses() {
        assertFalse(RunStatuses.isTerminal("RUNNING"));
        assertFalse(RunStatuses.isTerminal("AWAITING_APPROVAL"));
        assertFalse(RunStatuses.isTerminal(RunStatuses.AWAITING_CHILD_WORKFLOW));
        assertFalse(RunStatuses.isTerminal(null));
        assertFalse(RunStatuses.isTerminal("NONSENSE"));
    }
}
