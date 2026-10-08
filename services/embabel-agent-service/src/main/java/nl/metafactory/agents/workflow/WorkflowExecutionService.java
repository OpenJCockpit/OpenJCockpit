package nl.metafactory.agents.workflow;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowStartInput;
import nl.metafactory.agents.workflow.model.WorkflowStartResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a WorkflowDefinition's referenced agents/subagents/skills/MCP tools and starts an
 * execution via the existing AgentOrchestrator — it does not run a second execution engine.
 * MCP-tool references are resolved for traceability only today; the fixed pipeline stages are
 * gated by agent id (see EmbabelOrchestrator), further filtered here by policy decisions.
 *
 * <p>Every start path (dashboard button, Hermes signal, project-folder file) calls
 * PolicyDecisionService first — this is the Java-runtime guard, not something an agent can
 * choose to skip. Subagent/skill denials have no independent execution boundary to block in the
 * current runtime, so they cascade: a denied subagent excludes its parentAgent's pipeline stage;
 * a denied skill (which has no single owning agent in this data model) blocks the whole start.
 *
 * <p>workflow-trigger-workflow-orb architecture §6.2/AC-45: every public entry point below
 * requires an explicit {@link RunInitiator}, carried through unchanged into the
 * {@link AgentRunRequest} built in {@link #doStart}. Identity is never re-derived from the
 * thread-local security context here — that context does not exist on a background execution
 * path such as a workflow-orb-triggered child start. {@link #doStart} fails loudly
 * ({@link NullPointerException}) if a null initiator ever reaches it, rather than silently
 * defaulting.
 */
@Service
public class WorkflowExecutionService implements ChildWorkflowStarter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowDefinitionRepository workflowRepository;
    private final AgentSpecRepository agentSpecRepository;
    private final SubagentSpecRepository subagentSpecRepository;
    private final SkillSpecRepository skillSpecRepository;
    private final AgentOrchestrator orchestrator;
    private final PolicyDecisionService policyDecisionService;
    private final CurrentUserProvider currentUserProvider;

    public WorkflowExecutionService(WorkflowDefinitionRepository workflowRepository,
                                     AgentSpecRepository agentSpecRepository,
                                     SubagentSpecRepository subagentSpecRepository,
                                     SkillSpecRepository skillSpecRepository,
                                     AgentOrchestrator orchestrator,
                                     PolicyDecisionService policyDecisionService,
                                     CurrentUserProvider currentUserProvider) {
        this.workflowRepository = workflowRepository;
        this.agentSpecRepository = agentSpecRepository;
        this.subagentSpecRepository = subagentSpecRepository;
        this.skillSpecRepository = skillSpecRepository;
        this.orchestrator = orchestrator;
        this.policyDecisionService = policyDecisionService;
        this.currentUserProvider = currentUserProvider;
    }

    public WorkflowStartResponse startWorkflow(String workflowId, WorkflowStartInput input, RunInitiator initiator) {
        WorkflowDefinition workflow = loadWorkflow(workflowId);
        String triggerSource = "dashboard-button";
        PolicyDecision decision = policyDecisionService.canStartWorkflow(
                buildContext(workflow, triggerSource, null, null, initiator));
        if (!decision.allowed()) {
            return blocked(workflowId, decision);
        }
        return doStart(workflow, input, initiator, triggerSource, List.of(workflow.id()));
    }

    public WorkflowStartResponse startWorkflowFromHermesSignal(String workflowId, String signalType, RunInitiator initiator) {
        WorkflowDefinition workflow = loadWorkflow(workflowId);
        String triggerSource = "hermes-signal";
        PolicyDecision decision = policyDecisionService.canAcceptHermesSignal(
                buildContext(workflow, triggerSource, null, null, initiator));
        if (!decision.allowed()) {
            return blocked(workflowId, decision);
        }
        return doStart(workflow, WorkflowStartInput.empty(), initiator, triggerSource, List.of(workflow.id()));
    }

    public WorkflowStartResponse startWorkflowFromProjectFile(String workflowId, String filePath, String fileEventType, RunInitiator initiator) {
        WorkflowDefinition workflow = loadWorkflow(workflowId);
        String triggerSource = "project-folder-file";
        PolicyDecision decision = policyDecisionService.canProcessProjectFile(
                buildContext(workflow, triggerSource, filePath, fileEventType, initiator));
        if (!decision.allowed()) {
            return blocked(workflowId, decision);
        }
        return doStart(workflow, WorkflowStartInput.empty(), initiator, triggerSource, List.of(workflow.id()));
    }

    WorkflowStartResponse doStart(WorkflowDefinition workflow, WorkflowStartInput input,
                                   RunInitiator initiator, String triggerSource, List<String> chainAncestry) {
        Objects.requireNonNull(initiator, "initiator must not be null — identity must be carried explicitly, never silently defaulted");
        if (workflow.workflowOrbs() != null) {
            for (WorkflowOrb orb : workflow.workflowOrbs()) {
                String placementStage = orb.placementStage();
                if (placementStage != null && !placementStage.isBlank()
                        && (workflow.agentIds() == null || !workflow.agentIds().contains(placementStage))) {
                    return new WorkflowStartResponse(workflow.id(), null, "BLOCKED", Instant.now(),
                            "Workflow orb anchor stage '" + placementStage + "' is not among this workflow's selected agents");
                }
            }
        }
        String workflowId = workflow.id();
        List<String> availableAgentIds = orchestrator.availableAgents().stream().map(AgentDefinition::id).toList();
        List<String> candidateAgentIds = workflow.agentIds() == null ? List.of()
                : workflow.agentIds().stream().filter(availableAgentIds::contains).toList();

        List<String> resolvedAgentIds = applyPolicyFilter(workflow, candidateAgentIds, triggerSource, initiator);

        if (candidateAgentIds.isEmpty()) {
            log.warn("Workflow {} has no pipeline agents configured; blocking start", workflowId);
            return new WorkflowStartResponse(workflowId, null, "BLOCKED", Instant.now(),
                    "Workflow '" + workflowId + "' has no pipeline agents configured; select at least one agent in Design → Workflows.");
        }

        if (resolvedAgentIds.isEmpty()) {
            log.warn("Workflow {} — all pipeline agents were denied by policy; blocking start", workflowId);
            return new WorkflowStartResponse(workflowId, null, "BLOCKED", Instant.now(),
                    "All pipeline agents of workflow '" + workflowId + "' were denied by policy; nothing to run.");
        }

        long resolvedAgentSpecs = countResolved(workflow.agentIds(), agentSpecRepository::findByName);
        long resolvedSubagentSpecs = countResolved(workflow.subagentNames(), subagentSpecRepository::findByName);
        long resolvedSkillSpecs = countResolved(workflow.skillNames(), skillSpecRepository::findByName);
        log.info("Starting workflow {} — {} pipeline agent(s) after policy filtering, {} agent spec(s), {} subagent spec(s), {} skill spec(s) resolved",
                workflowId, resolvedAgentIds.size(), resolvedAgentSpecs, resolvedSubagentSpecs, resolvedSkillSpecs);

        String customerId = workflow.execution() != null && workflow.execution().customerId() != null
                ? workflow.execution().customerId() : workflow.projectName();
        // The repository URL of the selected project (passed at start) takes precedence over the
        // workflow's (static) execution configuration, so that the spec branch is pushed to the
        // repository of the active project.
        String repositoryUrl = input.repositoryUrl() != null && !input.repositoryUrl().isBlank()
                ? input.repositoryUrl()
                : workflow.execution() != null && workflow.execution().repositoryUrl() != null
                    ? workflow.execution().repositoryUrl() : "";

        String startedBy = currentUserProvider.currentUsername().orElse(null);
        // MADP-54: baseBranch has no ExecutionConfig counterpart (spec §4 non-goal) — the start
        // input is the only source. Blank → null, which the publishers fall back on
        // (metafactory.spec-git.base-branch). Resolved once here, per run.
        String baseBranch = input.baseBranch() != null && !input.baseBranch().isBlank()
                ? input.baseBranch() : null;

        var runRequest = new AgentRunRequest(customerId, resolveSpecPayload(workflow, input), resolvedAgentIds,
                "workflow:" + workflowId, repositoryUrl, input.gitUsername(), input.gitToken(),
                workflow.approvalGate(), baseBranch, workflowId, startedBy, initiator, chainAncestry);
        var run = orchestrator.start(runRequest);

        return new WorkflowStartResponse(workflowId, run.runId(), run.status(), run.startedAt(),
                "Workflow started with agents: " + resolvedAgentIds);
    }

    @Override
    public WorkflowStartResponse startWorkflowFromOrb(ChildStartCommand command) {
        if (command.initiator() == null) {
            throw new IllegalStateException("child start without a resolved initiator");
        }
        WorkflowDefinition target = loadWorkflow(command.targetWorkflowId());
        String triggerSource = "workflow-trigger";
        PolicyDecision decision = policyDecisionService.canStartWorkflow(
                buildContext(target, triggerSource, null, null, command.initiator()));
        if (!decision.allowed()) {
            return blocked(command.targetWorkflowId(), decision);
        }
        AgentRunRequest parentRequest = command.parentRequest();
        WorkflowStartInput reprojectedInput = new WorkflowStartInput(
                parentRequest.specFile(), null, parentRequest.repositoryUrl(),
                parentRequest.gitUsername(), parentRequest.gitToken(), parentRequest.baseBranch());
        List<String> childChainAncestry = new java.util.ArrayList<>(command.chainAncestry());
        childChainAncestry.add(target.id());
        return doStart(target, reprojectedInput, command.initiator(), triggerSource, childChainAncestry);
    }

    /**
     * The AgentRunRequest carries the "spec" the agents work on. An explicit
     * prompt from the dialog takes precedence over the (ambient) selected spec file and is
     * preceded by the workflow's promptInstructions — these state, among other things,
     * that the agent must create a branch, create the spec in specs/ based on the template
     * and push the branch for a pull request. Without
     * input, the workflow description keeps the behaviour from before WorkflowStartInput.
     */
    private String resolveSpecPayload(WorkflowDefinition workflow, WorkflowStartInput input) {
        if (input.prompt() != null && !input.prompt().isBlank()) {
            return prefixedPrompt(workflow, input.prompt());
        }
        if (input.specFile() != null && !input.specFile().isBlank()) {
            return input.specFile();
        }
        return workflow.description();
    }

    private String prefixedPrompt(WorkflowDefinition workflow, String prompt) {
        if (workflow.promptInstructions() == null || workflow.promptInstructions().isBlank()) {
            return prompt;
        }
        return workflow.promptInstructions() + "\n\n" + prompt;
    }

    /**
     * Filters candidateAgentIds by policy. A skill denial (no single owning agent in this data
     * model) blocks the whole workflow; a subagent denial excludes its parentAgent's stage; an
     * agent denial excludes that agent's stage directly.
     */
    private List<String> applyPolicyFilter(WorkflowDefinition workflow, List<String> candidateAgentIds, String triggerSource, RunInitiator initiator) {
        for (String skillName : orEmpty(workflow.skillNames())) {
            var context = buildEntityContext(workflow, "skill.execute", null, null, skillName, null, triggerSource, initiator);
            PolicyDecision decision = policyDecisionService.canExecuteSkill(context);
            if (!decision.allowed()) {
                log.warn("Workflow {} — skill {} denied by policy ({}); blocking all agents this start",
                        workflow.id(), skillName, decision.reason());
                return List.of();
            }
        }

        List<String> afterSubagentFilter = new ArrayList<>(candidateAgentIds);
        for (String subagentName : orEmpty(workflow.subagentNames())) {
            var subagentSpec = subagentSpecRepository.findByName(subagentName).orElse(null);
            var context = buildEntityContext(workflow, "subagent.use", null, subagentName, null, null, triggerSource, initiator);
            PolicyDecision decision = policyDecisionService.canUseSubagent(context);
            if (!decision.allowed() && subagentSpec != null && subagentSpec.parentAgent() != null) {
                log.warn("Workflow {} — subagent {} denied by policy ({}); excluding parent agent {}",
                        workflow.id(), subagentName, decision.reason(), subagentSpec.parentAgent());
                afterSubagentFilter.remove(subagentSpec.parentAgent());
            }
        }

        List<String> allowed = new ArrayList<>();
        for (String agentId : afterSubagentFilter) {
            var context = buildEntityContext(workflow, "agent.use", agentId, null, null, null, triggerSource, initiator);
            PolicyDecision decision = policyDecisionService.canUseAgent(context);
            if (decision.allowed()) {
                allowed.add(agentId);
            } else {
                log.warn("Workflow {} — agent {} denied by policy ({})", workflow.id(), agentId, decision.reason());
            }
        }
        return allowed;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private WorkflowStartResponse blocked(String workflowId, PolicyDecision decision) {
        return new WorkflowStartResponse(workflowId, null, "BLOCKED", Instant.now(), decision.reason());
    }

    private WorkflowDefinition loadWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
    }

    private PolicyDecisionContext buildContext(WorkflowDefinition workflow, String triggerSource,
                                                String filePath, String fileEventType, RunInitiator initiator) {
        String customerId = workflow.execution() != null ? workflow.execution().customerId() : null;
        String userId = initiator != null ? initiator.username() : null;
        return new PolicyDecisionContext(workflow.id(), null, null, workflow.projectName(), customerId, null,
                null, triggerSource, null, null, null, null, null, null, null, null, "workflow.start",
                userId, null, null, null, null, filePath, fileEventType, Instant.now(), Map.of());
    }

    private PolicyDecisionContext buildEntityContext(WorkflowDefinition workflow, String action,
                                                      String agentId, String subagentId, String skillId,
                                                      String mcpToolName, String triggerSource, RunInitiator initiator) {
        String customerId = workflow.execution() != null ? workflow.execution().customerId() : null;
        String userId = initiator != null ? initiator.username() : null;
        return new PolicyDecisionContext(workflow.id(), null, null, workflow.projectName(), customerId, null,
                null, triggerSource, agentId, null, subagentId, null, skillId, null, mcpToolName, null,
                action, userId, null, null, null, null, null, null, Instant.now(), Map.of());
    }

    private long countResolved(List<String> names, java.util.function.Function<String, java.util.Optional<?>> lookup) {
        if (names == null) return 0;
        return names.stream().filter(n -> lookup.apply(n).isPresent()).count();
    }
}
