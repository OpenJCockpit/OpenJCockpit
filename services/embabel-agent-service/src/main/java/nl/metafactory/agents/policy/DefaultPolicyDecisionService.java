package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.config.OpaProperties;
import nl.metafactory.agents.policy.model.OpaDecisionRequest;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.policy.model.PolicyDecisionAuditEntry;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disabled -&gt; allow (no OPA call, audited as SKIPPED_DISABLED). Enabled+unreachable -&gt; apply the
 * configured fail mode (audited as POLICY_UNAVAILABLE). Enabled+reachable -&gt; call OPA, map the
 * response, audit ALLOWED/DENIED. Every path writes an audit entry and — for actions scoped to a
 * workflow execution — caches the raw decision so repeated canContinueWorkflowStep-style checks
 * within the same run don't re-call OPA. The cache is process-lifetime only (no TTL/eviction),
 * which is an accepted simplification at this scale.
 */
@Service
public class DefaultPolicyDecisionService implements PolicyDecisionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPolicyDecisionService.class);

    private final OpenPolicyAgentClient client;
    private final OpaProperties properties;
    private final PolicyDecisionAuditRepository auditRepository;
    private final Map<String, PolicyDecision> decisionCache = new ConcurrentHashMap<>();

    public DefaultPolicyDecisionService(OpenPolicyAgentClient client, OpaProperties properties,
                                         PolicyDecisionAuditRepository auditRepository) {
        this.client = client;
        this.properties = properties;
        this.auditRepository = auditRepository;
    }

    @Override
    public PolicyDecision canStartWorkflow(PolicyDecisionContext context) {
        return decide("workflow.start", context);
    }

    @Override
    public PolicyDecision canUseAgent(PolicyDecisionContext context) {
        return decide("agent.use", context);
    }

    @Override
    public PolicyDecision canUseSubagent(PolicyDecisionContext context) {
        return decide("subagent.use", context);
    }

    @Override
    public PolicyDecision canExecuteSkill(PolicyDecisionContext context) {
        return decide("skill.execute", context);
    }

    @Override
    public PolicyDecision canInvokeMcpTool(PolicyDecisionContext context) {
        return decide("mcp.tool.invoke", context);
    }

    @Override
    public PolicyDecision canProcessProjectFile(PolicyDecisionContext context) {
        return decide("file.process", context);
    }

    @Override
    public PolicyDecision canAcceptHermesSignal(PolicyDecisionContext context) {
        return decide("hermes.signal.accept", context);
    }

    @Override
    public PolicyDecision canContinueWorkflowStep(PolicyDecisionContext context) {
        return decide("workflow.step.continue", context);
    }

    private PolicyDecision decide(String action, PolicyDecisionContext rawContext) {
        PolicyDecisionContext context = normalize(rawContext, action);

        String cacheKey = cacheKey(context);
        if (cacheKey != null) {
            PolicyDecision cached = decisionCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        PolicyDecision decision = evaluate(context);

        audit(context, decision);
        if (cacheKey != null) {
            decisionCache.put(cacheKey, decision);
        }
        return decision;
    }

    private PolicyDecision evaluate(PolicyDecisionContext context) {
        if (!properties.isEnabled()) {
            return PolicyDecision.opaDisabled("OPA is disabled", Instant.now());
        }
        if (!client.isReachable()) {
            log.warn("OPA is enabled but unreachable for action {} — applying fail mode {}",
                    context.action(), properties.getFailMode());
            return PolicyDecision.unavailable(properties.getFailMode(), "OPA is enabled but not reachable", Instant.now());
        }
        try {
            var response = client.evaluate(new OpaDecisionRequest(context));
            var result = response.result();
            return new PolicyDecision(result.allowed(), result.reason(), result.requiredApproval(),
                    result.riskLevel(), result.auditTags() != null ? result.auditTags() : List.of(),
                    response.decisionId(), properties.getPolicyPath(), null, Instant.now(), false, null);
        } catch (Exception e) {
            log.warn("OPA evaluation failed for action {} — applying fail mode {}: {}",
                    context.action(), properties.getFailMode(), e.getMessage());
            return PolicyDecision.unavailable(properties.getFailMode(), "OPA evaluation failed: " + e.getMessage(), Instant.now());
        }
    }

    private void audit(PolicyDecisionContext context, PolicyDecision decision) {
        if (!properties.isDecisionLoggingEnabled()) {
            return;
        }
        String result = decisionResult(decision);
        var entry = new PolicyDecisionAuditEntry(
                UUID.randomUUID().toString(),
                context.workflowId(), context.workflowExecutionId(), context.projectId(), context.projectName(),
                context.customerId(), context.customerName(), context.agentId(), context.subagentId(),
                context.skillId(), context.mcpToolName(), context.triggerSource(), context.action(),
                decision.opaDecisionId(), result, decision.reason(), decision.riskLevel(),
                decision.requiredApproval(), decision.auditTags(), decision.decisionTimestamp(),
                decision.policyRevision(), decision.policyUnavailable(), decision.failModeApplied());
        try {
            auditRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist policy decision audit entry for action {}: {}", context.action(), e.getMessage());
        }
    }

    private String decisionResult(PolicyDecision decision) {
        if (!properties.isEnabled()) {
            return "SKIPPED_DISABLED";
        }
        if (decision.policyUnavailable()) {
            return "POLICY_UNAVAILABLE";
        }
        return decision.allowed() ? "ALLOWED" : "DENIED";
    }

    private String cacheKey(PolicyDecisionContext context) {
        if (context.workflowExecutionId() == null) {
            return null;
        }
        String entity = String.join("|",
                nullToEmpty(context.agentId()), nullToEmpty(context.subagentId()),
                nullToEmpty(context.skillId()), nullToEmpty(context.mcpToolName()));
        return context.workflowExecutionId() + "::" + context.action() + "::" + entity;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private PolicyDecisionContext normalize(PolicyDecisionContext raw, String action) {
        return new PolicyDecisionContext(
                raw.workflowId(), raw.workflowExecutionId(), raw.projectId(),
                orDefault(raw.projectName(), properties.getProjectLabel()),
                raw.customerId(),
                orDefault(raw.customerName(), properties.getCustomerLabel()),
                orDefault(raw.environment(), properties.getEnvironment()),
                raw.triggerSource(), raw.agentId(), raw.agentName(), raw.subagentId(), raw.subagentName(),
                raw.skillId(), raw.skillName(), raw.mcpToolName(), raw.mcpToolOperation(),
                action, raw.userId(), raw.userRole(), raw.projectClassification(), raw.humanApproval(),
                raw.inputDocumentType(), raw.filePath(), raw.fileEventType(),
                raw.timestamp() != null ? raw.timestamp() : Instant.now(),
                raw.additionalAttributes() != null ? raw.additionalAttributes() : Map.of());
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
