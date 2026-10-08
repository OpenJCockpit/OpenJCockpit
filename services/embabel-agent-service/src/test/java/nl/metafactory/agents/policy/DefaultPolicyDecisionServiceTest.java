package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.FailMode;
import nl.metafactory.agents.policy.model.OpaDecisionResponse;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPolicyDecisionServiceTest {

    private OpenPolicyAgentClient client;
    private OpaProperties properties;
    private PolicyDecisionAuditRepository auditRepository;
    private DefaultPolicyDecisionService service;

    @BeforeEach
    void setUp() {
        client = mock(OpenPolicyAgentClient.class);
        properties = new OpaProperties();
        auditRepository = mock(PolicyDecisionAuditRepository.class);
        service = new DefaultPolicyDecisionService(client, properties, auditRepository);
    }

    private PolicyDecisionContext context(String workflowExecutionId) {
        return new PolicyDecisionContext("wf-1", workflowExecutionId, "project-1", "Project One",
                "customer-1", "Customer One", null, "dashboard-button", "requirements-agent", "Requirements Agent",
                null, null, null, null, null, null, null, null, null, null, false, null, null, null,
                Instant.now(), Map.of());
    }

    @Test
    void opaDisabledAllowsAndAuditsAsSkipped() {
        properties.setEnabled(false);

        var decision = service.canStartWorkflow(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyUnavailable()).isFalse();
        verify(client, never()).isReachable();
        verify(client, never()).evaluate(any());
        verify(auditRepository).save(argThat(e -> "SKIPPED_DISABLED".equals(e.opaDecisionResult())));
    }

    @Test
    void opaEnabledAndReachableAllows() {
        properties.setEnabled(true);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1"));

        var decision = service.canStartWorkflow(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.opaDecisionId()).isEqualTo("decision-1");
        assertThat(decision.policyUnavailable()).isFalse();
        verify(auditRepository).save(argThat(e -> "ALLOWED".equals(e.opaDecisionResult())));
    }

    @Test
    void opaEnabledAndReachableDenies() {
        properties.setEnabled(true);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(false, "requires human approval", true, "high",
                        List.of("agent-tool-governance")), "decision-2"));

        var decision = service.canUseAgent(context("exec-1"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.requiredApproval()).isTrue();
        assertThat(decision.riskLevel()).isEqualTo("high");
        verify(auditRepository).save(argThat(e -> "DENIED".equals(e.opaDecisionResult()) && e.requiredApproval()));
    }

    @Test
    void opaEnabledButUnreachableFailClosedDenies() {
        properties.setEnabled(true);
        properties.setFailMode(FailMode.FAIL_CLOSED);
        when(client.isReachable()).thenReturn(false);

        var decision = service.canStartWorkflow(context("exec-1"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.policyUnavailable()).isTrue();
        assertThat(decision.failModeApplied()).isEqualTo("FAIL_CLOSED");
        verify(client, never()).evaluate(any());
        verify(auditRepository).save(argThat(e -> "POLICY_UNAVAILABLE".equals(e.opaDecisionResult())));
    }

    @Test
    void opaEnabledButUnreachableFailOpenAllows() {
        properties.setEnabled(true);
        properties.setFailMode(FailMode.FAIL_OPEN);
        when(client.isReachable()).thenReturn(false);

        var decision = service.canStartWorkflow(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyUnavailable()).isTrue();
        assertThat(decision.failModeApplied()).isEqualTo("FAIL_OPEN");
    }

    @Test
    void opaEvaluationThrowsAppliesFailMode() {
        properties.setEnabled(true);
        properties.setFailMode(FailMode.FAIL_CLOSED);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenThrow(new OpaClientException("boom", new RuntimeException("boom")));

        var decision = service.canInvokeMcpTool(context("exec-1"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.policyUnavailable()).isTrue();
    }

    @Test
    void auditingCanBeDisabled() {
        properties.setEnabled(false);
        properties.setDecisionLoggingEnabled(false);

        service.canStartWorkflow(context("exec-1"));

        verify(auditRepository, never()).save(any());
    }

    @Test
    void cachesDecisionPerWorkflowExecutionAndAction() {
        properties.setEnabled(true);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1"));

        service.canContinueWorkflowStep(context("exec-1"));
        service.canContinueWorkflowStep(context("exec-1"));

        verify(client, times(1)).evaluate(any());
        verify(auditRepository, times(1)).save(any());
    }

    @Test
    void doesNotCacheAcrossDifferentActions() {
        properties.setEnabled(true);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1"));

        service.canStartWorkflow(context("exec-1"));
        service.canUseAgent(context("exec-1"));

        verify(client, times(2)).evaluate(any());
    }

    @Test
    void doesNotCacheWhenWorkflowExecutionIdIsNull() {
        properties.setEnabled(true);
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1"));

        service.canAcceptHermesSignal(context(null));
        service.canAcceptHermesSignal(context(null));

        verify(client, times(2)).evaluate(any());
    }

    @Test
    void appliesLabelFallbacksFromOpaPropertiesWhenContextFieldsBlank() {
        properties.setEnabled(true);
        properties.setEnvironment("prod");
        properties.setCustomerLabel("Fallback Customer");
        properties.setProjectLabel("Fallback Project");
        when(client.isReachable()).thenReturn(true);
        when(client.evaluate(any())).thenReturn(new OpaDecisionResponse(
                new OpaDecisionResponse.OpaResult(true, "allowed", false, "low", List.of()), "decision-1"));

        var blankContext = new PolicyDecisionContext("wf-1", "exec-1", "project-1", "", "customer-1", "",
                "", "dashboard-button", null, null, null, null, null, null, null, null, null, null, null,
                null, false, null, null, null, Instant.now(), Map.of());

        service.canStartWorkflow(blankContext);

        var captor = org.mockito.ArgumentCaptor.forClass(nl.metafactory.agents.policy.model.OpaDecisionRequest.class);
        verify(client).evaluate(captor.capture());
        assertThat(captor.getValue().input().environment()).isEqualTo("prod");
        assertThat(captor.getValue().input().customerName()).isEqualTo("Fallback Customer");
        assertThat(captor.getValue().input().projectName()).isEqualTo("Fallback Project");
    }

    @Test
    void doesNotPropagateWhenAuditPersistenceFails() {
        properties.setEnabled(false);
        org.mockito.Mockito.doThrow(new RuntimeException("disk full")).when(auditRepository).save(any());

        var decision = service.canStartWorkflow(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void canUseSubagentDelegatesToDecide() {
        properties.setEnabled(false);

        var decision = service.canUseSubagent(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void canExecuteSkillDelegatesToDecide() {
        properties.setEnabled(false);

        var decision = service.canExecuteSkill(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void canProcessProjectFileDelegatesToDecide() {
        properties.setEnabled(false);

        var decision = service.canProcessProjectFile(context("exec-1"));

        assertThat(decision.allowed()).isTrue();
    }

    private static nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry argThat(
            java.util.function.Predicate<nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
