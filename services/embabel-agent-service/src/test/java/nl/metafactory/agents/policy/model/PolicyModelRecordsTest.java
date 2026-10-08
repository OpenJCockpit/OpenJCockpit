package nl.metafactory.agents.policy.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyModelRecordsTest {

    @Test
    void policyDecisionContext() {
        var now = Instant.now();
        var r = new PolicyDecisionContext("wf-1", "exec-1", "project-123", "public-sector-case-system",
                "customer-456", "Example Government Customer", "prod", "project-folder-file",
                "requirements-agent", "Requirements Agent", "jira-analysis-subagent", "Jira Analysis Subagent",
                "extract-requirements", "Extract Requirements", "jira.createIssue", "create", "tool.invoke",
                "user-1", "delivery-lead", "government-sensitive", false, "requirements-document",
                "/documents/spec.md", "CREATED", now, Map.of());

        assertThat(r.workflowId()).isEqualTo("wf-1");
        assertThat(r.workflowExecutionId()).isEqualTo("exec-1");
        assertThat(r.customerName()).isEqualTo("Example Government Customer");
        assertThat(r.mcpToolName()).isEqualTo("jira.createIssue");
        assertThat(r.mcpToolOperation()).isEqualTo("create");
        assertThat(r.action()).isEqualTo("tool.invoke");
        assertThat(r.projectClassification()).isEqualTo("government-sensitive");
        assertThat(r.humanApproval()).isFalse();
        assertThat(r.timestamp()).isEqualTo(now);
        assertThat(r.additionalAttributes()).isEmpty();
    }

    @Test
    void policyDecisionOpaDisabledFactory() {
        var now = Instant.now();
        var decision = PolicyDecision.opaDisabled("OPA is disabled", now);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("OPA is disabled");
        assertThat(decision.requiredApproval()).isFalse();
        assertThat(decision.auditTags()).containsExactly("opa-disabled");
        assertThat(decision.policyUnavailable()).isFalse();
        assertThat(decision.failModeApplied()).isNull();
        assertThat(decision.decisionTimestamp()).isEqualTo(now);
    }

    @Test
    void policyDecisionUnavailableFactoryFailClosedDenies() {
        var now = Instant.now();
        var decision = PolicyDecision.unavailable(FailMode.FAIL_CLOSED, "OPA unreachable", now);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.policyUnavailable()).isTrue();
        assertThat(decision.failModeApplied()).isEqualTo("FAIL_CLOSED");
        assertThat(decision.auditTags()).containsExactly("policy-unavailable");
    }

    @Test
    void policyDecisionUnavailableFactoryFailOpenAllows() {
        var decision = PolicyDecision.unavailable(FailMode.FAIL_OPEN, "OPA unreachable", Instant.now());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyUnavailable()).isTrue();
        assertThat(decision.failModeApplied()).isEqualTo("FAIL_OPEN");
    }

    @Test
    void opaDecisionRequestWrapsInput() {
        var context = new PolicyDecisionContext("wf-1", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, Instant.now(), Map.of());
        var request = new OpaDecisionRequest(context);

        assertThat(request.input()).isEqualTo(context);
        assertThat(request.input().workflowId()).isEqualTo("wf-1");
    }

    @Test
    void opaDecisionResponseMapsResultAndDecisionId() {
        var result = new OpaDecisionResponse.OpaResult(false, "requires approval", true, "high",
                List.of("agent-tool-governance", "human-in-the-loop"));
        var response = new OpaDecisionResponse(result, "4ca636c1-55e4-417a-b1d8-4aceb67960d1");

        assertThat(response.result().allowed()).isFalse();
        assertThat(response.result().reason()).isEqualTo("requires approval");
        assertThat(response.result().requiredApproval()).isTrue();
        assertThat(response.result().riskLevel()).isEqualTo("high");
        assertThat(response.result().auditTags()).contains("human-in-the-loop");
        assertThat(response.decisionId()).isEqualTo("4ca636c1-55e4-417a-b1d8-4aceb67960d1");
    }

    @Test
    void policyDecisionAuditEntry() {
        var now = Instant.now();
        var entry = new PolicyDecisionAuditEntry("audit-1", "wf-1", "exec-1", "project-123", "Project Name",
                "customer-456", "Customer Name", "requirements-agent", "jira-analysis-subagent",
                "extract-requirements", "jira.createIssue", "dashboard-button", "workflow.start",
                "4ca636c1-55e4-417a-b1d8-4aceb67960d1", "DENIED", "requires human approval", "high",
                true, List.of("agent-tool-governance"), now, "v3", false, null);

        assertThat(entry.id()).isEqualTo("audit-1");
        assertThat(entry.opaDecisionResult()).isEqualTo("DENIED");
        assertThat(entry.riskLevel()).isEqualTo("high");
        assertThat(entry.requiredApproval()).isTrue();
        assertThat(entry.auditTags()).containsExactly("agent-tool-governance");
        assertThat(entry.policyRevision()).isEqualTo("v3");
        assertThat(entry.policyUnavailable()).isFalse();
        assertThat(entry.timestamp()).isEqualTo(now);
    }
}
