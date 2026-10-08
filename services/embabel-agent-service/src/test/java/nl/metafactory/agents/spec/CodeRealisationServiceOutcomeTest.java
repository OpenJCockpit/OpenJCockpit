package nl.metafactory.agents.spec;

import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.domain.CodeChangeSet;
import nl.metafactory.agents.domain.FileChange;
import nl.metafactory.agents.domain.FileSelection;
import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.spec.GitToolClient.GitToolOutcome;
import nl.metafactory.agents.subagent.CodeRealisationAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AC-27/AC-62 server half: every failure step of {@link CodeRealisationService#realise} maps to
 * the correct {@link StageOutcome}, and {@code toPublication()} preserves today's exact
 * {@link SpecPublication} for each case — asserted here as one table across every step, distinct
 * from {@code CodeRealisationServiceTest}'s scenario-level (tool-call sequencing, prompt content)
 * coverage.
 */
class CodeRealisationServiceOutcomeTest {

    private static final String RUN_ID = "0a1b2c3d-e4f5-6789-abcd-ef0123456789";
    private static final String REPO_URL = "https://github.com/org/repo.git";

    private GitToolClient git;
    private CodeRealisationAgent agent;
    private SpecGitProperties properties;
    private CodeRealisationService service;
    private final Map<String, GitToolOutcome> failures = new HashMap<>();
    private List<String> repoFiles = List.of("src/App.java");

    private final ImplementationPlan plan = new ImplementationPlan("spec-1.md", List.of("change"), "arch");
    private final TestPlan tests = new TestPlan("spec-1.md", List.of("test"), "90%");
    private final ReviewReport review = new ReviewReport("spec-1.md", true, List.of());

    @BeforeEach
    void setUp() {
        git = mock(GitToolClient.class);
        agent = mock(CodeRealisationAgent.class);
        properties = new SpecGitProperties();
        service = new CodeRealisationService(git, properties, agent);

        doAnswer(inv -> {
            String tool = inv.getArgument(3);
            if (failures.containsKey(tool)) {
                return failures.get(tool);
            }
            return switch (tool) {
                case "git_list_files" -> new GitToolOutcome(true, "listed", null, null, repoFiles);
                case "git_read_file" -> new GitToolOutcome(true, "read", null, "content", null);
                case "git_create_pull_request" -> new GitToolOutcome(true, "created",
                        "https://github.com/org/repo/pull/9", null, null);
                default -> new GitToolOutcome(true, "ok", null, null, null);
            };
        }).when(git).call(any(), any(), any(), anyString(), anyMap());

        when(agent.selectFiles(anyString(), anyList())).thenReturn(new FileSelection(List.of()));
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("summary", List.of(new FileChange("src/App.java", "content"))));
    }

    private AgentRunRequest request() {
        return new AgentRunRequest("cust", "spec-1.md", List.of("realisation"),
                "workflow:wf-1", REPO_URL, "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private void assertOutcomeAndPublicationParity(StageOutcome expected, String expectedPublicationStatus) {
        var report = service.realise(RUN_ID, request(), plan, tests, review, RealisationIteration.first());
        assertThat(report.outcome()).isEqualTo(expected);
        assertThat(report.toPublication().status()).isEqualTo(expectedPublicationStatus);
    }

    @Test
    void disabledPropertyIsNotApplicable() {
        properties.setEnabled(false);
        assertOutcomeAndPublicationParity(StageOutcome.NOT_APPLICABLE, "SKIPPED");
    }

    @Test
    void blankRepositoryUrlIsNotApplicable() {
        var report = service.realise(RUN_ID, new AgentRunRequest("cust", "spec-1.md", List.of("realisation"),
                "workflow:wf-1", "  ", "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test")), plan, tests, review, RealisationIteration.first());

        assertThat(report.outcome()).isEqualTo(StageOutcome.NOT_APPLICABLE);
        assertThat(report.toPublication().status()).isEqualTo("SKIPPED");
    }

    @Test
    void branchCreationFailureIsPublishFailed() {
        failures.put("git_create_branch", new GitToolOutcome(false, "clone broken", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }

    @Test
    void listFilesFailureIsPublishFailed() {
        failures.put("git_list_files", new GitToolOutcome(false, "list broken", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }

    @Test
    void noImplementableChangesIsNoChange() {
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("nothing", List.of()));
        assertOutcomeAndPublicationParity(StageOutcome.NO_CHANGE, "FAILED");
    }

    @Test
    void writeFileFailureIsPublishFailed() {
        failures.put("git_write_file", new GitToolOutcome(false, "disk full", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }

    @Test
    void commitFailureWithGitMcpServersExactWordingIsNoChange() {
        failures.put("git_commit", new GitToolOutcome(false,
                "Nothing to commit: workspace has no uncommitted changes", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.NO_CHANGE, "FAILED");
    }

    @Test
    void commitFailureWithAnyOtherWordingIsPublishFailed() {
        failures.put("git_commit", new GitToolOutcome(false, "disk full", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }

    @Test
    void pushFailureIsPublishFailed() {
        failures.put("git_push", new GitToolOutcome(false, "no write permissions", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }

    @Test
    void pullRequestFailureIsStillPublished() {
        // AC-62's guided-retry precondition: a PR failure alone does not fail the stage — the
        // branch and commit already landed. See CodeRealisationServiceIterationTest (batch B8)
        // for the iteration-2 PR-retention behaviour this outcome enables.
        failures.put("git_create_pull_request", new GitToolOutcome(false, "token missing", null, null, null));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISHED, "OK");
    }

    @Test
    void cleanRunIsPublished() {
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISHED, "OK");
    }

    @Test
    void unexpectedExceptionIsPublishFailed() {
        when(agent.implement(anyString(), any(), anyMap(), any())).thenThrow(new IllegalStateException("llm broken"));
        assertOutcomeAndPublicationParity(StageOutcome.PUBLISH_FAILED, "FAILED");
    }
}
