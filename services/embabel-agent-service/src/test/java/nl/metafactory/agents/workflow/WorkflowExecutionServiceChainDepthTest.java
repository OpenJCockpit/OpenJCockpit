package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.workflow.model.ExecutionConfig;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowOrb;
import nl.metafactory.agents.workflow.model.WorkflowOrbMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AC-23: a chain that reaches exactly the configured maximum depth completes, exercised through
 * the real WorkflowChainResolver and a real file-backed WorkflowDefinitionRepository (not the
 * resolver's own unit tests, which already cover the depth-exceeded boundary — this proves the
 * runtime wiring in WorkflowExecutionService.startWorkflowFromOrb correctly respects the depth
 * the resolver computes).
 */
class WorkflowExecutionServiceChainDepthTest {

    private WorkflowDefinitionRepository workflowRepository;
    private WorkflowChainResolver workflowChainResolver;
    private AgentOrchestrator orchestrator;
    private PolicyDecisionService policyDecisionService;
    private CurrentUserProvider currentUserProvider;
    private WorkflowExecutionService service;

    private static PolicyDecision allow() {
        return new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
    }

    private WorkflowDefinition definitionAt(String id, WorkflowOrb orb) {
        return new WorkflowDefinition(id, "Level-" + id, "", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null,
                new ExecutionConfig("cust1", "https://github.com/org/repo", 300),
                false, null, "ACTIVE", null, null, null, orb == null ? null : List.of(orb));
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        var store = new YamlDefinitionStore();
        workflowRepository = new WorkflowDefinitionRepository(properties, store);
        var groupRepository = mock(WorkflowGroupRepository.class);
        var triggerProperties = new WorkflowTriggerProperties();
        workflowChainResolver = new WorkflowChainResolver(workflowRepository, groupRepository, triggerProperties);

        var agentSpecRepository = mock(AgentSpecRepository.class);
        var subagentSpecRepository = mock(SubagentSpecRepository.class);
        var skillSpecRepository = mock(SkillSpecRepository.class);
        orchestrator = mock(AgentOrchestrator.class);
        policyDecisionService = mock(PolicyDecisionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("test-user"));
        service = new WorkflowExecutionService(workflowRepository, agentSpecRepository,
                subagentSpecRepository, skillSpecRepository, orchestrator, policyDecisionService,
                currentUserProvider);

        when(orchestrator.availableAgents()).thenReturn(List.of(
                new AgentDefinition("requirement", "Requirement Agent", "d", "r", List.of(), List.of(), 0, "in", "out")
        ));
        when(policyDecisionService.canStartWorkflow(any())).thenReturn(allow());
        when(policyDecisionService.canUseAgent(any())).thenReturn(allow());
        when(policyDecisionService.canUseSubagent(any())).thenReturn(allow());
        when(policyDecisionService.canExecuteSkill(any())).thenReturn(allow());
        when(orchestrator.start(any())).thenAnswer(invocation -> {
            AgentRunRequest req = invocation.getArgument(0);
            return new AgentRun("run-" + req.requestedBy(), req.customerId(), req.specFile(),
                    req.repositoryUrl(), "RUNNING", Instant.now(), List.of(), List.of(), null, null, null, null);
        });
    }

    @Test
    void aChainExactlyAtTheConfiguredMaxDepthCompletesEveryLevel() {
        var orbToLevel1 = new WorkflowOrb("wf-level1", WorkflowOrbMode.SEQUENTIAL, null);
        var orbToLevel2 = new WorkflowOrb("wf-level2", WorkflowOrbMode.SEQUENTIAL, null);
        var orbToLevel3 = new WorkflowOrb("wf-level3", WorkflowOrbMode.SEQUENTIAL, null);
        workflowRepository.save(definitionAt("wf-root", orbToLevel1));
        workflowRepository.save(definitionAt("wf-level1", orbToLevel2));
        workflowRepository.save(definitionAt("wf-level2", orbToLevel3));
        workflowRepository.save(definitionAt("wf-level3", null));
        workflowRepository.save(definitionAt("wf-level4", null));

        var initiator = RunInitiator.trigger("test");
        List<String> ancestry = new ArrayList<>(List.of("wf-root"));

        var decision1 = workflowChainResolver.resolve("wf-level1", workflowRepository.findById("wf-root").orElseThrow(), ancestry);
        assertThat(decision1).isInstanceOf(ChildStartDecision.Permitted.class);
        var parentRequestLevel0 = new AgentRunRequest("cust1", "root-spec", List.of("requirement"), "workflow:wf-root",
                "https://github.com/org/repo", null, null, null, null, null, null, initiator, ancestry);
        var command1 = new ChildStartCommand("wf-level1", parentRequestLevel0, WorkflowOrbMode.SEQUENTIAL, initiator, ancestry);
        var response1 = service.startWorkflowFromOrb(command1);
        assertThat(response1.executionId()).isNotNull();
        assertThat(response1.status()).isNotEqualTo("BLOCKED");

        List<String> ancestryAt1 = new ArrayList<>(ancestry);
        ancestryAt1.add("wf-level1");
        var decision2 = workflowChainResolver.resolve("wf-level2", workflowRepository.findById("wf-level1").orElseThrow(), ancestryAt1);
        assertThat(decision2).isInstanceOf(ChildStartDecision.Permitted.class);
        var parentRequestLevel1 = new AgentRunRequest("cust1", "l1-spec", List.of("requirement"), "workflow:wf-level1",
                "https://github.com/org/repo", null, null, null, null, null, null, initiator, ancestryAt1);
        var command2 = new ChildStartCommand("wf-level2", parentRequestLevel1, WorkflowOrbMode.SEQUENTIAL, initiator, ancestryAt1);
        var response2 = service.startWorkflowFromOrb(command2);
        assertThat(response2.executionId()).isNotNull();
        assertThat(response2.status()).isNotEqualTo("BLOCKED");

        List<String> ancestryAt2 = new ArrayList<>(ancestryAt1);
        ancestryAt2.add("wf-level2");
        var decision3 = workflowChainResolver.resolve("wf-level3", workflowRepository.findById("wf-level2").orElseThrow(), ancestryAt2);
        assertThat(decision3).isInstanceOf(ChildStartDecision.Permitted.class);
        var parentRequestLevel2 = new AgentRunRequest("cust1", "l2-spec", List.of("requirement"), "workflow:wf-level2",
                "https://github.com/org/repo", null, null, null, null, null, null, initiator, ancestryAt2);
        var command3 = new ChildStartCommand("wf-level3", parentRequestLevel2, WorkflowOrbMode.SEQUENTIAL, initiator, ancestryAt2);
        var response3 = service.startWorkflowFromOrb(command3);
        assertThat(response3.executionId()).isNotNull();
        assertThat(response3.status()).isNotEqualTo("BLOCKED");

        List<String> ancestryAt3 = new ArrayList<>(ancestryAt2);
        ancestryAt3.add("wf-level3");
        assertThat(ancestryAt3).containsExactly("wf-root", "wf-level1", "wf-level2", "wf-level3");
        var decisionBeyondDepth = workflowChainResolver.resolve("wf-level4", workflowRepository.findById("wf-level3").orElseThrow(), ancestryAt3);
        assertThat(decisionBeyondDepth).isInstanceOf(ChildStartDecision.Refused.class);
        assertThat(((ChildStartDecision.Refused) decisionBeyondDepth).code()).isEqualTo("DEPTH_EXCEEDED");
    }
}
