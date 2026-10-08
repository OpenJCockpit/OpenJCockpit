package nl.metafactory.agents.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStatusesTest {

    @Test
    void terminalStatusesAreRecognised() {
        assertThat(AgentRunStatuses.isTerminal("COMPLETED")).isTrue();
        assertThat(AgentRunStatuses.isTerminal("FAILED")).isTrue();
        assertThat(AgentRunStatuses.isTerminal("CANCELLED")).isTrue();
        assertThat(AgentRunStatuses.isTerminal("DENIED")).isTrue();
        assertThat(AgentRunStatuses.isTerminal("BLOCKED")).isTrue();
        assertThat(AgentRunStatuses.isTerminal("TIMED_OUT")).isTrue();
    }

    @Test
    void nonTerminalStatusesAreNotRecognised() {
        assertThat(AgentRunStatuses.isTerminal("AWAITING_APPROVAL")).isFalse();
        assertThat(AgentRunStatuses.isTerminal("RUNNING")).isFalse();
        assertThat(AgentRunStatuses.isTerminal(null)).isFalse();
        assertThat(AgentRunStatuses.isTerminal("NOT_A_REAL_STATUS")).isFalse();
    }

    @Test
    void openStatusesAreNonNullAndNonTerminal() {
        assertThat(AgentRunStatuses.isOpen("RUNNING")).isTrue();
        assertThat(AgentRunStatuses.isOpen("AWAITING_APPROVAL")).isTrue();
        assertThat(AgentRunStatuses.isOpen("COMPLETED")).isFalse();
        assertThat(AgentRunStatuses.isOpen("FAILED")).isFalse();
        assertThat(AgentRunStatuses.isOpen("CANCELLED")).isFalse();
        assertThat(AgentRunStatuses.isOpen("DENIED")).isFalse();
        assertThat(AgentRunStatuses.isOpen(null)).isFalse();
    }
}
