package nl.metafactory.agents.gitworkspace;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitWorkspaceTicketsTest {

    private static final GitWorkspaceBinding WS = new GitWorkspaceBinding("job-1", "https://r/repo.git", "openjcockpit/wf/abc");

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-16T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final GitWorkspaceTickets tickets = new GitWorkspaceTickets(new GitWorkspaceProperties(), clock);

    @Test
    void aTicketRedeemsOnceForItsWorkflowAndSubject() {
        var issued = tickets.issue(WS, "wf-1", "alice");

        assertThat(issued.ticket()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(issued.toString()).doesNotContain(issued.ticket());
        assertThat(tickets.redeem(issued.ticket(), "wf-1", "alice")).contains(WS);
        assertThat(tickets.redeem(issued.ticket(), "wf-1", "alice")).isEmpty();
    }

    @Test
    void aMismatchUsesTheTicketUp() {
        var issued = tickets.issue(WS, "wf-1", "alice");

        assertThat(tickets.redeem(issued.ticket(), "wf-1", "mallory")).isEmpty();
        assertThat(tickets.redeem(issued.ticket(), "wf-1", "alice")).isEmpty();
    }

    @Test
    void anExpiredTicketDoesNotRedeem() {
        var issued = tickets.issue(WS, "wf-1", "alice");
        now.set(now.get().plus(Duration.ofMinutes(3)));

        assertThat(tickets.redeem(issued.ticket(), "wf-1", "alice")).isEmpty();
    }

    @Test
    void ticketsAreUniqueAndNeedAWorkflowAndSubject() {
        assertThat(tickets.issue(WS, "wf-1", "alice").ticket()).isNotEqualTo(tickets.issue(WS, "wf-1", "alice").ticket());
        assertThatThrownBy(() -> tickets.issue(WS, " ", "alice")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue(WS, "wf-1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tickets.redeem(null, "wf-1", "alice")).isEmpty();
    }

    @Test
    void theNumberOfOutstandingTicketsIsCapped() {
        for (int i = 0; i < GitWorkspaceTickets.MAX_OUTSTANDING; i++) {
            tickets.issue(WS, "wf-1", "alice");
        }
        assertThatThrownBy(() -> tickets.issue(WS, "wf-1", "alice")).isInstanceOf(IllegalStateException.class);
        now.set(now.get().plus(Duration.ofMinutes(3)));
        assertThat(tickets.issue(WS, "wf-1", "alice").ticket()).isNotBlank();
    }

    @Test
    void theBindingRejectsUnsafeBranches() {
        for (String bad : new String[]{null, "", "-x", "a..b", "a b", "a\nb"}) {
            assertThatThrownBy(() -> new GitWorkspaceBinding("job-1", "https://r", bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
