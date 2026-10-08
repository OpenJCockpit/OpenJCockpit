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
import nl.metafactory.agents.subagent.ReviewerFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-21 / R2 (the single most likely implementation trap) and R3 (PR-URL retention): the loop-back
 * re-run at iteration ≥2 must use {@code git_checkout_branch}, never {@code git_create_branch}, and
 * must skip {@code git_create_pull_request} entirely — not call-and-swallow it — when a PR URL is
 * already retained.
 */
class CodeRealisationServiceIterationTest {

    private static final String RUN_ID = "0a1b2c3d-e4f5-6789-abcd-ef0123456789";
    private static final String REPO_URL = "https://github.com/org/repo.git";
    private static final String BRANCH = "feat/wf-spec-realise-0a1b2c3d";

    private record ToolCall(String tool, Map<String, Object> payload) {}

    private GitToolClient git;
    private CodeRealisationAgent agent;
    private SpecGitProperties properties;
    private CodeRealisationService service;
    private final List<ToolCall> calls = new ArrayList<>();
    private final Map<String, GitToolOutcome> failures = new HashMap<>();
    private List<String> repoFiles = List.of("src/App.java");
    private final Map<String, String> repoContents = new HashMap<>();

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
            Map<String, Object> payload = inv.getArgument(4);
            calls.add(new ToolCall(tool, payload));
            if (failures.containsKey(tool)) {
                return failures.get(tool);
            }
            return switch (tool) {
                case "git_list_files" -> new GitToolOutcome(true, "listed", null, null, repoFiles);
                case "git_read_file" -> {
                    String content = repoContents.get((String) payload.get("path"));
                    yield content != null
                            ? new GitToolOutcome(true, "read", null, content, null)
                            : new GitToolOutcome(false, "File not found", null, null, null);
                }
                case "git_create_pull_request" -> new GitToolOutcome(true, "Created pull request",
                        "https://github.com/org/repo/pull/999", null, null);
                default -> new GitToolOutcome(true, "ok", null, null, null);
            };
        }).when(git).call(any(), any(), any(), anyString(), anyMap());

        when(agent.selectFiles(anyString(), anyList())).thenReturn(new FileSelection(List.of()));
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("Adjusted based on feedback",
                        List.of(new FileChange("src/App.java", "class App { /* v2 */ }"))));
    }

    private AgentRunRequest request() {
        return new AgentRunRequest("cust", "spec-1.md", List.of("realisation"),
                "workflow:wf-spec-realise", REPO_URL, "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private AgentRunRequest requestWithBase(String baseBranch) {
        return new AgentRunRequest("cust", "spec-1.md", List.of("realisation"),
                "workflow:wf-spec-realise", REPO_URL, "bot", "secret", null, baseBranch, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private List<String> toolSequence() {
        return calls.stream().map(ToolCall::tool).toList();
    }

    private List<ToolCall> callsFor(String tool) {
        return calls.stream().filter(c -> c.tool().equals(tool)).toList();
    }

    @Test
    void iterationOneStillUsesGitCreateBranch() {
        service.realise(RUN_ID, request(), plan, tests, review, RealisationIteration.first());

        assertThat(toolSequence()).contains("git_create_branch");
        assertThat(toolSequence()).doesNotContain("git_checkout_branch");
    }

    @Test
    void iterationTwoUsesGitCheckoutBranchAndNeverGitCreateBranch() {
        var iteration = new RealisationIteration(2, "please rename the method", List.of("src/App.java"),
                BRANCH, "https://github.com/org/repo/pull/9");

        var result = service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(toolSequence()).contains("git_checkout_branch");
        assertThat(toolSequence()).doesNotContain("git_create_branch");
        assertThat(callsFor("git_checkout_branch").get(0).payload())
                .containsEntry("repositoryUrl", REPO_URL)
                .containsEntry("branch", BRANCH)
                .containsEntry("username", "bot")
                .containsEntry("token", "secret");
        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
    }

    @Test
    void iterationTwoPassesReviewerFeedbackToTheAgentWithPreviousChangedPaths() {
        var iteration = new RealisationIteration(2, "<distinctive feedback text>",
                List.of("src/App.java", "src/Billing.java"), BRANCH, "https://github.com/org/repo/pull/9");

        service.realise(RUN_ID, request(), plan, tests, review, iteration);

        var captor = org.mockito.ArgumentCaptor.forClass(ReviewerFeedback.class);
        verify(agent).implement(anyString(), any(), anyMap(), captor.capture());
        assertThat(captor.getValue().iteration()).isEqualTo(2);
        assertThat(captor.getValue().comment()).isEqualTo("<distinctive feedback text>");
        assertThat(captor.getValue().previousChangedPaths()).containsExactly("src/App.java", "src/Billing.java");
    }

    @Test
    void iterationOnePassesNullReviewerFeedback() {
        service.realise(RUN_ID, request(), plan, tests, review, RealisationIteration.first());

        var captor = org.mockito.ArgumentCaptor.forClass(ReviewerFeedback.class);
        verify(agent).implement(anyString(), any(), anyMap(), captor.capture());
        assertThat(captor.getValue()).isNull();
    }

    @Test
    void iterationTwoWithARetainedPullRequestUrlSkipsGitCreatePullRequestEntirelyAndReUsesTheUrl() {
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"),
                BRANCH, "https://github.com/org/repo/pull/9");

        var result = service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(toolSequence()).doesNotContain("git_create_pull_request");
        assertThat(result.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
        assertThat(result.message()).contains("https://github.com/org/repo/pull/9");
    }

    @Test
    void iterationTwoWithNoRetainedPullRequestUrlLegitimatelyRetriesPrCreation() {
        // AC-62's guided retry: iteration 1 ended PUBLISH_FAILED with no PR, so iteration 2
        // correctly attempts a fresh git_create_pull_request rather than skipping it forever.
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"), BRANCH, null);

        var result = service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(toolSequence()).contains("git_create_pull_request");
        assertThat(result.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/999");
    }

    @Test
    void iterationTwoProducesAnAdditiveCommitWithNoForcePushNoAmendNoBranchDelete() {
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"),
                BRANCH, "https://github.com/org/repo/pull/9");

        service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(toolSequence()).containsSubsequence("git_checkout_branch", "git_write_file", "git_commit", "git_push");
        assertThat(toolSequence()).doesNotContain("git_push_force", "git_delete_branch", "git_amend");
        assertThat((String) callsFor("git_commit").get(0).payload().get("message"))
                .contains("(review iteration 2)");
    }

    @Test
    void checkoutBranchFailureIsPublishFailedNotBranchCreationWording() {
        failures.put("git_checkout_branch", new GitToolOutcome(false, "workspace broken", null, null, null));
        var iteration = new RealisationIteration(2, "feedback", List.of(), BRANCH, null);

        var result = service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(result.message()).contains("Failed to switch branch").contains("workspace broken");
        assertThat(toolSequence()).doesNotContain("git_create_branch");
    }

    @Test
    void iterationOneCommitMessageHasNoIterationSuffix() {
        service.realise(RUN_ID, request(), plan, tests, review, RealisationIteration.first());

        assertThat((String) callsFor("git_commit").get(0).payload().get("message"))
                .doesNotContain("review iteration");
    }

    // ── MADP-54 AC-06 / Q5 : a base branch on the request changes no iteration-≥2 payload ──

    @Test
    void iterationTwoCheckoutPayloadHasExactKeySetAndNoBaseBranchEvenWhenTheRequestCarriesOne() {
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"),
                BRANCH, "https://github.com/org/repo/pull/9");

        var result = service.realise(RUN_ID, requestWithBase("develop"), plan, tests, review, iteration);

        assertThat(callsFor("git_checkout_branch").get(0).payload().keySet())
                .containsExactlyInAnyOrder("repositoryUrl", "branch", "username", "token");
        assertThat(toolSequence()).doesNotContain("git_create_branch");
        assertThat(toolSequence()).doesNotContain("git_create_pull_request");
        assertThat(result.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/9");
    }

    @Test
    void iterationTwoWithoutRetainedUrlPutsTheResolvedBaseOnlyOnTheRecreatedPullRequest() {
        // Q5: even here the base is the one resolved at start; git_checkout_branch still carries none.
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"), BRANCH, null);

        service.realise(RUN_ID, requestWithBase("develop"), plan, tests, review, iteration);

        assertThat(callsFor("git_checkout_branch").get(0).payload()).doesNotContainKey("baseBranch");
        assertThat(callsFor("git_create_pull_request").get(0).payload()).containsEntry("baseBranch", "develop");
    }

    @Test
    void noNewToolNamesAreUsedAcrossIterationsOnlyTheExistingSet() {
        // The re-run must use exactly the same GitToolClient path and the same tool names as
        // iteration 1 — no capability widening.
        var iteration = new RealisationIteration(2, "feedback", List.of("src/App.java"), BRANCH, null);

        service.realise(RUN_ID, request(), plan, tests, review, iteration);

        assertThat(toolSequence()).containsOnly("git_checkout_branch", "git_list_files", "git_write_file",
                "git_commit", "git_push", "git_create_pull_request");
    }
}
