package nl.metafactory.agents.workflow;

import nl.metafactory.agents.agent.EvidenceAgent;
import nl.metafactory.agents.agent.ImpactAnalysisAgent;
import nl.metafactory.agents.agent.ImplementationAgent;
import nl.metafactory.agents.agent.RequirementAgent;
import nl.metafactory.agents.agent.ReviewAgent;
import nl.metafactory.agents.agent.TestDesignAgent;
import nl.metafactory.agents.approval.ApprovalGateCoordinator;
import nl.metafactory.agents.approval.ApprovalGateRegistry;
import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.model.RunInitiator;
import nl.metafactory.agents.orchestration.AgentOrchestrator;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.AsyncPipelineRunner;
import nl.metafactory.agents.orchestration.EmbabelOrchestrator;
import nl.metafactory.agents.persistence.InMemoryAgentRunPersistence;
import nl.metafactory.agents.policy.PolicyDecisionService;
import nl.metafactory.agents.policy.model.PolicyDecisionContext;
import nl.metafactory.agents.policy.model.PolicyDecision;
import nl.metafactory.agents.security.CurrentUserProvider;
import nl.metafactory.agents.spec.CodeRealisationService;
import nl.metafactory.agents.spec.SpecGitPublisher;
import nl.metafactory.agents.spec.SpecPublication;
import nl.metafactory.agents.workflow.model.WorkflowStartInput;
import nl.metafactory.agents.workflowtrigger.WorkflowOrbRunner;
import nl.metafactory.agents.workflowtrigger.WorkflowTriggerCoordinator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * THE MANDATED PROOF (workflow-execution-state-to-database, T-01, AC-04/06/07): drives one run of
 * a real {@link WorkflowExecutionService} + real {@link WorkflowDefinitionRepository} + real
 * {@link YamlDefinitionStore} + real {@link EmbabelOrchestrator} + real {@link AgentRunStore}
 * (only the leaf domain agents are mocked, mirroring the established pattern in
 * {@code EmbabelOrchestratorDefinitionFailureEventTest}) to each of COMPLETED, FAILED, CANCELLED
 * and BLOCKED, and asserts on the ACTUAL on-disk workflow-definition file's SHA-256 digest, byte
 * size and last-modified time — captured before any of this module's code runs, and re-captured
 * after each terminal status. A Mockito {@code verify(..., never()).save(...)} (see
 * {@code WorkflowExecutionServiceTest}) is a fast, valuable complement, never a substitute: only a
 * real file-system comparison can prove the definition file was genuinely never rewritten.
 */
class WorkflowDefinitionFileUntouchedByRunIntegrationTest {

    @TempDir
    static Path definitionsRoot;

    private static Path workflowsDir;

    private static byte[] preRunCompletedBytes;
    private static long preRunCompletedSize;
    private static FileTime preRunCompletedMtime;
    private static String preRunCompletedSha256;

    private static byte[] preRunFailedBytes;
    private static long preRunFailedSize;
    private static FileTime preRunFailedMtime;
    private static String preRunFailedSha256;

    private static byte[] preRunCancelledBytes;
    private static long preRunCancelledSize;
    private static FileTime preRunCancelledMtime;
    private static String preRunCancelledSha256;

    private static byte[] preRunBlockedBytes;
    private static long preRunBlockedSize;
    private static FileTime preRunBlockedMtime;
    private static String preRunBlockedSha256;

    @BeforeAll
    static void writeFixturesAndCaptureFileEvidenceBeforeAnyModuleCodeRuns() throws IOException, NoSuchAlgorithmException {
        workflowsDir = definitionsRoot.resolve("workflows");
        Files.createDirectories(workflowsDir);

        writeFixture("wf-completed", "Completed fixture");
        writeFixture("wf-failed", "Failed fixture");
        writeFixture("wf-cancelled", "Cancelled fixture");
        writeFixture("wf-blocked", "Blocked fixture");

        Path completedFile = workflowsDir.resolve("wf-completed.yaml");
        preRunCompletedBytes = Files.readAllBytes(completedFile);
        preRunCompletedSize = Files.size(completedFile);
        preRunCompletedMtime = Files.getLastModifiedTime(completedFile);
        preRunCompletedSha256 = sha256Hex(preRunCompletedBytes);

        Path failedFile = workflowsDir.resolve("wf-failed.yaml");
        preRunFailedBytes = Files.readAllBytes(failedFile);
        preRunFailedSize = Files.size(failedFile);
        preRunFailedMtime = Files.getLastModifiedTime(failedFile);
        preRunFailedSha256 = sha256Hex(preRunFailedBytes);

        Path cancelledFile = workflowsDir.resolve("wf-cancelled.yaml");
        preRunCancelledBytes = Files.readAllBytes(cancelledFile);
        preRunCancelledSize = Files.size(cancelledFile);
        preRunCancelledMtime = Files.getLastModifiedTime(cancelledFile);
        preRunCancelledSha256 = sha256Hex(preRunCancelledBytes);

        Path blockedFile = workflowsDir.resolve("wf-blocked.yaml");
        preRunBlockedBytes = Files.readAllBytes(blockedFile);
        preRunBlockedSize = Files.size(blockedFile);
        preRunBlockedMtime = Files.getLastModifiedTime(blockedFile);
        preRunBlockedSha256 = sha256Hex(preRunBlockedBytes);
    }

    private static void writeFixture(String id, String name) throws IOException {
        Path file = workflowsDir.resolve(id + ".yaml");
        Files.writeString(file, """
                id: %s
                name: "%s"
                projectName: "Test Project"
                description: desc
                agentIds:
                  - requirement
                status: ACTIVE
                """.formatted(id, name), StandardOpenOption.CREATE);
    }

    private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static void assertFileUntouched(String id, byte[] preBytes, long preSize, FileTime preMtime, String preSha256)
            throws IOException, NoSuchAlgorithmException {
        Path file = workflowsDir.resolve(id + ".yaml");
        byte[] postBytes = Files.readAllBytes(file);
        assertThat(postBytes).isEqualTo(preBytes);
        assertThat(Files.size(file)).isEqualTo(preSize);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(preMtime);
        assertThat(sha256Hex(postBytes)).isEqualTo(preSha256);
    }

    private WorkflowDefinitionRepository buildRepository() {
        WorkflowDefinitionProperties properties = new WorkflowDefinitionProperties();
        properties.setPath(definitionsRoot.toString());
        return new WorkflowDefinitionRepository(properties, new YamlDefinitionStore());
    }

    private static AgentPipelineProperties buildPipelineProperties() {
        var props = new AgentPipelineProperties();
        props.setSequence(List.of("requirement"));
        return props;
    }

    private EmbabelOrchestrator buildOrchestrator(AgentRunStore runStore, RequirementAgent requirementAgent) {
        AsyncPipelineRunner asyncPipelineRunner = mock(AsyncPipelineRunner.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(asyncPipelineRunner).run(any());

        SpecGitPublisher specGitPublisher = mock(SpecGitPublisher.class);
        when(specGitPublisher.publish(any(), any(), any())).thenReturn(SpecPublication.skipped("skipped in test"));

        return new EmbabelOrchestrator(requirementAgent, mock(ImpactAnalysisAgent.class),
                mock(TestDesignAgent.class), mock(ImplementationAgent.class), mock(ReviewAgent.class),
                mock(EvidenceAgent.class), buildPipelineProperties(), asyncPipelineRunner, specGitPublisher,
                mock(CodeRealisationService.class), runStore, mock(ApprovalGateCoordinator.class),
                mock(ApprovalGateRegistry.class), mock(WorkflowOrbRunner.class),
                mock(WorkflowTriggerCoordinator.class));
    }

    private WorkflowExecutionService buildService(WorkflowDefinitionRepository repository,
                                                   AgentOrchestrator orchestrator,
                                                   PolicyDecisionService policyDecisionService) {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("test-user"));
        return new WorkflowExecutionService(repository, mock(AgentSpecRepository.class),
                mock(SubagentSpecRepository.class), mock(SkillSpecRepository.class), orchestrator,
                policyDecisionService, currentUserProvider);
    }

    private static PolicyDecisionService allowAllPolicy() {
        PolicyDecisionService policy = mock(PolicyDecisionService.class);
        PolicyDecision allow = new PolicyDecision(true, "allowed", false, "low", List.of(), null, null, null, Instant.now(), false, null);
        when(policy.canStartWorkflow(any(PolicyDecisionContext.class))).thenReturn(allow);
        when(policy.canUseAgent(any(PolicyDecisionContext.class))).thenReturn(allow);
        when(policy.canUseSubagent(any(PolicyDecisionContext.class))).thenReturn(allow);
        when(policy.canExecuteSkill(any(PolicyDecisionContext.class))).thenReturn(allow);
        return policy;
    }

    @Test
    void definitionFileIsUntouchedAfterARunCompletesSuccessfully() throws Exception {
        WorkflowDefinitionRepository repository = buildRepository();
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        RequirementAgent requirementAgent = mock(RequirementAgent.class);
        when(requirementAgent.analyzeRequirements(any())).thenAnswer(inv -> {
            var spec = inv.getArgument(0, nl.metafactory.agents.domain.SpecContent.class);
            return new RequirementAnalysis(spec.specId(), List.of(), "ok");
        });
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore, requirementAgent);
        WorkflowExecutionService service = buildService(repository, orchestrator, allowAllPolicy());

        var response = service.startWorkflow("wf-completed", WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var run = orchestrator.get(response.executionId());
        assertThat(run.status()).isEqualTo("COMPLETED");
        assertFileUntouched("wf-completed", preRunCompletedBytes, preRunCompletedSize, preRunCompletedMtime, preRunCompletedSha256);
    }

    @Test
    void definitionFileIsUntouchedAfterARunFailsThroughTheRealOrchestratorCatchPath() throws Exception {
        WorkflowDefinitionRepository repository = buildRepository();
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        RequirementAgent requirementAgent = mock(RequirementAgent.class);
        when(requirementAgent.analyzeRequirements(any())).thenThrow(new IllegalStateException("simulated agent failure"));
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore, requirementAgent);
        WorkflowExecutionService service = buildService(repository, orchestrator, allowAllPolicy());

        var response = service.startWorkflow("wf-failed", WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var run = orchestrator.get(response.executionId());
        assertThat(run.status()).isEqualTo("FAILED");
        assertFileUntouched("wf-failed", preRunFailedBytes, preRunFailedSize, preRunFailedMtime, preRunFailedSha256);
    }

    @Test
    void definitionFileIsUntouchedAfterARunIsCancelledMidPipeline() throws Exception {
        WorkflowDefinitionRepository repository = buildRepository();
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        RequirementAgent requirementAgent = mock(RequirementAgent.class);
        // Marks the run cancelled as a side effect of the requirement stage, so the pipeline's
        // next checkCancelled() call (at the start of the "impact" stage block) throws
        // CancellationException — a genuine mid-pipeline cancellation, not a stubbed shortcut.
        when(requirementAgent.analyzeRequirements(any())).thenAnswer(inv -> {
            var spec = inv.getArgument(0, nl.metafactory.agents.domain.SpecContent.class);
            runStore.markCancelled(spec.specId());
            return new RequirementAnalysis(spec.specId(), List.of(), "ok");
        });
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore, requirementAgent);
        WorkflowExecutionService service = buildService(repository, orchestrator, allowAllPolicy());

        var response = service.startWorkflow("wf-cancelled", WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        var run = orchestrator.get(response.executionId());
        assertThat(run.status()).isEqualTo("CANCELLED");
        assertFileUntouched("wf-cancelled", preRunCancelledBytes, preRunCancelledSize, preRunCancelledMtime, preRunCancelledSha256);
    }

    @Test
    void definitionFileIsUntouchedWhenARunIsBlockedByPolicyBeforeTheOrchestratorIsEverCalled() throws Exception {
        WorkflowDefinitionRepository repository = buildRepository();
        AgentRunStore runStore = new AgentRunStore(new InMemoryAgentRunPersistence(), event -> { });
        RequirementAgent requirementAgent = mock(RequirementAgent.class);
        EmbabelOrchestrator orchestrator = buildOrchestrator(runStore, requirementAgent);
        PolicyDecisionService policy = mock(PolicyDecisionService.class);
        PolicyDecision deny = new PolicyDecision(false, "blocked for test", false, "high", List.of(), null, null, null, Instant.now(), false, null);
        when(policy.canStartWorkflow(any(PolicyDecisionContext.class))).thenReturn(deny);
        WorkflowExecutionService service = buildService(repository, orchestrator, policy);

        var response = service.startWorkflow("wf-blocked", WorkflowStartInput.empty(), RunInitiator.trigger("test"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertFileUntouched("wf-blocked", preRunBlockedBytes, preRunBlockedSize, preRunBlockedMtime, preRunBlockedSha256);
    }
}
