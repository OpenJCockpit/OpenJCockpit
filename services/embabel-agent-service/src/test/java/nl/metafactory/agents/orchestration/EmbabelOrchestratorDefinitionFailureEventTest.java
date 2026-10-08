package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.spec.CodeRealisationService;
import nl.metafactory.agents.spec.SpecGitPublisher;
import nl.metafactory.agents.workflow.DefinitionFileReadException;
import nl.metafactory.agents.workflow.YamlDefinitionStore;
import nl.metafactory.agents.workflow.model.SkillSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class EmbabelOrchestratorDefinitionFailureEventTest {

    private RequirementAgent requirementAgent;
    private AsyncPipelineRunner asyncPipelineRunner;

    @BeforeEach
    void setUp() {
        requirementAgent = mock(RequirementAgent.class);
        asyncPipelineRunner = mock(AsyncPipelineRunner.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(asyncPipelineRunner).run(any());
    }

    private static AgentPipelineProperties buildProps() {
        var props = new AgentPipelineProperties();
        props.setSequence(List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"));
        return props;
    }

    private EmbabelOrchestrator buildOrchestrator(AgentRunStore runStore) {
        return new EmbabelOrchestrator(requirementAgent, mock(ImpactAnalysisAgent.class),
                mock(TestDesignAgent.class), mock(ImplementationAgent.class), mock(ReviewAgent.class),
                mock(EvidenceAgent.class), buildProps(), asyncPipelineRunner, mock(SpecGitPublisher.class),
                mock(CodeRealisationService.class), runStore, mock(ApprovalGateCoordinator.class),
                mock(ApprovalGateRegistry.class), mock(nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner.class),
                mock(nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator.class));
    }

    private static AgentRunRequest requestFor(String workflowId) {
        return new AgentRunRequest("cust1", "file.md",
                List.of("requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence"),
                "user", "", null, null, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), List.of(workflowId));
    }

    /**
     * Triggers a genuine DefinitionFileReadException via YamlDefinitionStore's public find(...)
     * method, since its constructor is intentionally package-private to nl.metafactory.agents.workflow
     * and therefore not directly constructible from this test's package.
     */
    private static DefinitionFileReadException realDefinitionFailure(Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("wf-x.yaml"), "- not\n- an\n- object\n");
        var store = new YamlDefinitionStore();
        try {
            store.find(tempDir, "wf-x", SkillSpec.class);
            throw new AssertionError("Expected a DefinitionFileReadException");
        } catch (DefinitionFileReadException e) {
            return e;
        }
    }

    @Test
    void pipelineRecordsExactlyOneWorkflowDefinitionFailedEventWhenTheDefinitionFileIsUnreadable(@TempDir Path tempDir) throws IOException {
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore);
        DefinitionFileReadException failure = realDefinitionFailure(tempDir);
        when(requirementAgent.analyzeRequirements(any())).thenThrow(failure);

        var started = orchestrator.start(requestFor("wf-x"));
        var result = orchestrator.get(started.runId());

        assertThat(result.status()).isEqualTo("FAILED");
        var definitionEvents = result.events().stream()
                .filter(e -> e.agentId().equals("workflow-definition"))
                .toList();
        assertThat(definitionEvents).hasSize(1);
        assertThat(definitionEvents.get(0).status()).isEqualTo("FAILED");
        assertThat(definitionEvents.get(0).title())
                .contains("wf-x.yaml")
                .contains(failure.sanitisedReason());
    }

    @Test
    void definitionFailureEventIsRecordedBeforeTheRunIsMarkedFailed(@TempDir Path tempDir) throws IOException {
        AgentRunStore realRunStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        AgentRunStore spiedRunStore = spy(realRunStore);
        EmbabelOrchestrator orchestrator = buildOrchestrator(spiedRunStore);
        DefinitionFileReadException failure = realDefinitionFailure(tempDir);
        when(requirementAgent.analyzeRequirements(any())).thenThrow(failure);

        var started = orchestrator.start(requestFor("wf-x"));

        InOrder order = inOrder(spiedRunStore);
        order.verify(spiedRunStore).recordEvent(
                eq(started.runId()), eq("workflow-definition"), anyString(), eq("FAILED"), anyString());
        order.verify(spiedRunStore).setStatus(eq(started.runId()), eq("FAILED"), anyString());
    }

    @Test
    void definitionFailureEventTitleLeaksNoAbsolutePathAndNoFileContent(@TempDir Path tempDir) throws IOException {
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore);
        DefinitionFileReadException failure = realDefinitionFailure(tempDir);
        when(requirementAgent.analyzeRequirements(any())).thenThrow(failure);

        var started = orchestrator.start(requestFor("wf-x"));
        var result = orchestrator.get(started.runId());

        var definitionEvent = result.events().stream()
                .filter(e -> e.agentId().equals("workflow-definition"))
                .findFirst().orElseThrow();

        assertThat(definitionEvent.title()).doesNotContain(tempDir.toAbsolutePath().toString());
        assertThat(definitionEvent.title()).doesNotContain("- not\n- an\n- object");
    }

    @Test
    void genericRuntimeExceptionRecordsNoWorkflowDefinitionEvent() {
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore);
        when(requirementAgent.analyzeRequirements(any())).thenThrow(new RuntimeException("ai failure"));

        var started = orchestrator.start(requestFor("wf-x"));
        var result = orchestrator.get(started.runId());

        assertThat(result.status()).isEqualTo("FAILED");
        var definitionEvents = result.events().stream()
                .filter(e -> e.agentId().equals("workflow-definition"))
                .toList();
        assertThat(definitionEvents).isEmpty();
    }
}
