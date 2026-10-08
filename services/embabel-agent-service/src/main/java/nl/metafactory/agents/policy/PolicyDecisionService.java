package nl.metafactory.agents.policy;

import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;

/**
 * The internal abstraction workflow execution, Embabel agents/subagents/skills, triggers, and the
 * MCP tool gateway call before performing a sensitive action. Agents never call OPA themselves —
 * this is the Java-runtime guard in front of OpenPolicyAgentClient:
 * Embabel agent -&gt; Java runtime guard -&gt; PolicyDecisionService -&gt; OpenPolicyAgentClient -&gt; OPA.
 */
public interface PolicyDecisionService {

    PolicyDecision canStartWorkflow(PolicyDecisionContext context);

    PolicyDecision canUseAgent(PolicyDecisionContext context);

    PolicyDecision canUseSubagent(PolicyDecisionContext context);

    PolicyDecision canExecuteSkill(PolicyDecisionContext context);

    PolicyDecision canInvokeMcpTool(PolicyDecisionContext context);

    PolicyDecision canProcessProjectFile(PolicyDecisionContext context);

    PolicyDecision canAcceptHermesSignal(PolicyDecisionContext context);

    PolicyDecision canContinueWorkflowStep(PolicyDecisionContext context);
}
