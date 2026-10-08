package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.model.DecisionLogFilter;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyDecisionLogExportServiceTest {

    private PolicyDecisionAuditRepository repository;
    private PolicyDecisionLogExportService service;

    private final Instant baseTime = Instant.parse("2026-07-04T12:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(PolicyDecisionAuditRepository.class);
        service = new PolicyDecisionLogExportService(repository);
    }

    private PolicyDecisionAuditEntry entry(String id, String workflowId, String workflowExecutionId,
                                            String agentId, String subagentId, String skillId,
                                            String mcpToolName, String result, Instant timestamp) {
        return new PolicyDecisionAuditEntry(id, workflowId, workflowExecutionId, "project-1", "Project One",
                "customer-1", "Customer One", agentId, subagentId, skillId, mcpToolName,
                "dashboard-button", "workflow.start", "decision-" + id, result, "reason", "low",
                false, List.of(), timestamp, null, false, null);
    }

    @Test
    void exportForWorkflowReturnsOnlyMatchingWorkflowId() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime),
                entry("2", "wf-2", "exec-2", null, null, null, null, "ALLOWED", baseTime)
        ));

        var result = service.exportDecisionLogsForWorkflow("wf-1", DecisionLogFilter.none());

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void exportForWorkflowExecutionReturnsOnlyMatchingExecutionId() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-2", null, null, null, null, "ALLOWED", baseTime)
        ));

        var result = service.exportDecisionLogsForWorkflowExecution("exec-2", DecisionLogFilter.none());

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("2");
    }

    @Test
    void filtersByTimeRange() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime.minus(1, ChronoUnit.DAYS)),
                entry("2", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime)
        ));

        var filter = new DecisionLogFilter(baseTime.minusSeconds(1), null, null, null, null, null, null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("2");
    }

    @Test
    void filtersByTimeRangeUpperBound() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime.plus(1, ChronoUnit.DAYS))
        ));

        var filter = new DecisionLogFilter(null, baseTime.plusSeconds(1), null, null, null, null, null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersByAgentId() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", "agent-a", null, null, null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", "agent-b", null, null, null, "ALLOWED", baseTime)
        ));

        var filter = new DecisionLogFilter(null, null, "agent-a", null, null, null, null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersBySubagentId() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, "sub-a", null, null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", null, "sub-b", null, null, "ALLOWED", baseTime)
        ));

        var filter = new DecisionLogFilter(null, null, null, "sub-a", null, null, null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersBySkillId() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, "skill-a", null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", null, null, "skill-b", null, "ALLOWED", baseTime)
        ));

        var filter = new DecisionLogFilter(null, null, null, null, "skill-a", null, null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersByMcpToolName() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, "jira.createIssue", "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", null, null, null, "github.createPr", "ALLOWED", baseTime)
        ));

        var filter = new DecisionLogFilter(null, null, null, null, null, "jira.createIssue", null);
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersByRequiresApprovalPseudoResult() {
        var requiresApproval = new PolicyDecisionAuditEntry("1", "wf-1", "exec-1", "project-1", "Project One",
                "customer-1", "Customer One", null, null, null, null, "dashboard-button", "workflow.start",
                "decision-1", "DENIED", "reason", "high", true, List.of(), baseTime, null, false, null);
        var noApproval = entry("2", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime);
        when(repository.findAll()).thenReturn(List.of(requiresApproval, noApproval));

        var filter = new DecisionLogFilter(null, null, null, null, null, null, "REQUIRES_APPROVAL");
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("1");
    }

    @Test
    void filtersByDecisionResult() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", null, null, null, null, "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-1", null, null, null, null, "DENIED", baseTime)
        ));

        var filter = new DecisionLogFilter(null, null, null, null, null, null, "DENIED");
        var result = service.exportDecisionLogsForWorkflow("wf-1", filter);

        assertThat(result).extracting(PolicyDecisionAuditEntry::id).containsExactly("2");
    }

    @Test
    void nullFilterReturnsAllMatchingWorkflow() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", "a", "s", "sk", "tool", "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-2", "b", null, null, null, "DENIED", baseTime)
        ));

        var result = service.exportDecisionLogsForWorkflow("wf-1", null);

        assertThat(result).hasSize(2);
    }

    @Test
    void noneFilterReturnsAllMatchingWorkflow() {
        when(repository.findAll()).thenReturn(List.of(
                entry("1", "wf-1", "exec-1", "a", "s", "sk", "tool", "ALLOWED", baseTime),
                entry("2", "wf-1", "exec-2", "b", null, null, null, "DENIED", baseTime)
        ));

        var result = service.exportDecisionLogsForWorkflow("wf-1", DecisionLogFilter.none());

        assertThat(result).hasSize(2);
    }
}
