package nl.metafactory.common.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionAuditEntryTest {

    @Test
    void carriesTheDecisionFields() {
        Instant now = Instant.now();
        var entry = new PolicyDecisionAuditEntry("d-1", "wf-1", "run-1", "p-1", "Project", "c-1", "Customer",
                "agent", "subagent", "skill", "git_push", "dashboard-button", "agent.use", "opa-1", "DENIED",
                "denied by policy", "high", true, List.of("agent.use"), now, "v1", false, null);

        assertThat(entry.id()).isEqualTo("d-1");
        assertThat(entry.opaDecisionResult()).isEqualTo("DENIED");
        assertThat(entry.requiredApproval()).isTrue();
        assertThat(entry.auditTags()).containsExactly("agent.use");
        assertThat(entry.timestamp()).isEqualTo(now);
        assertThat(entry.failModeApplied()).isNull();
    }
}
