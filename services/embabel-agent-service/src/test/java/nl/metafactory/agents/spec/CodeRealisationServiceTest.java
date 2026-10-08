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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CodeRealisationServiceTest {

    private static final String RUN_ID = "0a1b2c3d-e4f5-6789-abcd-ef0123456789";
    private static final String REPO_URL = "https://github.com/org/repo.git";
    private static final String PR_URL = "https://github.com/org/repo/pull/9";
    private static final String BRANCH = "feat/wf-spec-realise-0a1b2c3d";

    private GitToolClient git;
    private CodeRealisationAgent agent;
    private SpecGitProperties properties;
    private CodeRealisationService service;

    private record ToolCall(String tool, Map<String, Object> payload) {}
    private final List<ToolCall> calls = new ArrayList<>();
    private final Map<String, GitToolOutcome> failures = new HashMap<>();
    private List<String> repoFiles = List.of("src/App.java", "README.md", "specs/spec-1.md");
    private final Map<String, String> repoContents = new HashMap<>(Map.of(
            "src/App.java", "class App {}",
            "specs/spec-1.md", "# Feature spec 1"));

    private final ImplementationPlan plan = new ImplementationPlan("spec-1.md",
            List.of("Add validation"), "Existing architecture suffices");
    private final TestPlan tests = new TestPlan("spec-1.md", List.of("Test validation"), "90%");
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
                case "git_create_pull_request" -> new GitToolOutcome(true, "Created pull request", PR_URL, null, null);
                default -> new GitToolOutcome(true, "ok", null, null, null);
            };
        }).when(git).call(any(), any(), any(), anyString(), anyMap());

        when(agent.selectFiles(anyString(), anyList()))
                .thenReturn(new FileSelection(List.of("src/App.java", "does-not-exist.txt")));
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("Validation added",
                        List.of(new FileChange("src/App.java", "class App { /* new */ }"))));
    }

    private AgentRunRequest request(String specFile, String repositoryUrl) {
        return new AgentRunRequest("cust", specFile, List.of("realisation"),
                "workflow:wf-spec-realise", repositoryUrl, "bot", "secret", null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private AgentRunRequest requestWithBase(String repositoryUrl, String baseBranch) {
        return new AgentRunRequest("cust", "spec-1.md", List.of("realisation"),
                "workflow:wf-spec-realise", repositoryUrl, "bot", "secret", null, baseBranch, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private List<String> toolSequence() {
        return calls.stream().map(ToolCall::tool).toList();
    }

    private List<ToolCall> callsFor(String tool) {
        return calls.stream().filter(c -> c.tool().equals(tool)).toList();
    }

    @Test
    void realiseSkipsWhenDisabledOrWithoutRepositoryUrl() {
        properties.setEnabled(false);
        assertThat(service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first()).outcome())
                .isEqualTo(StageOutcome.NOT_APPLICABLE);

        properties.setEnabled(true);
        assertThat(service.realise(RUN_ID, request("spec-1.md", null), plan, tests, review, RealisationIteration.first()).outcome())
                .isEqualTo(StageOutcome.NOT_APPLICABLE);
        assertThat(service.realise(RUN_ID, request("spec-1.md", " "), plan, tests, review, RealisationIteration.first()).outcome())
                .isEqualTo(StageOutcome.NOT_APPLICABLE);
        verifyNoInteractions(git, agent);
    }

    @Test
    void realiseImplementsPlanAndOpensPullRequest() {
        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(result.branch()).isEqualTo(BRANCH);
        assertThat(result.pullRequestUrl()).isEqualTo(PR_URL);
        assertThat(result.message()).contains("2 files").contains("including new implementation plan")
                .contains("pull request: " + PR_URL);

        assertThat(toolSequence()).containsExactly(
                "git_create_branch",   // clones the repository and creates the feature branch
                "git_list_files",
                "git_read_file",       // feature spec
                "git_read_file",       // context file src/App.java
                "git_write_file",      // code change
                "git_write_file",      // new implementation plan in the same branch
                "git_commit", "git_push", "git_create_pull_request");

        assertThat(callsFor("git_create_branch").get(0).payload())
                .containsEntry("newBranch", BRANCH)
                .containsEntry("baseBranch", "main")
                .containsEntry("username", "bot")
                .containsEntry("token", "secret");

        var writes = callsFor("git_write_file");
        assertThat(writes.get(0).payload()).containsEntry("path", "src/App.java")
                .containsEntry("content", "class App { /* new */ }");
        assertThat(writes.get(1).payload()).containsEntry("path", "specs/implementation-0a1b2c3d.md");
        assertThat((String) writes.get(1).payload().get("content")).contains("Add validation");

        assertThat((String) callsFor("git_commit").get(0).payload().get("message"))
                .contains("Implement spec-1.md for run " + RUN_ID);
        assertThat(callsFor("git_create_pull_request").get(0).payload())
                .containsEntry("title", "Implement spec-1.md")
                .containsEntry("headBranch", BRANCH);
        assertThat((String) callsFor("git_create_pull_request").get(0).payload().get("body"))
                .contains("Validation added");

        // Widening what the gated stage reports (architecture §6.4): the change summary and the
        // changed-file paths now survive onto the report itself, for a later batch's approval
        // context to read — previously only changes.size() leaked into the commit message.
        assertThat(result.changeSummary()).isEqualTo("Validation added");
        assertThat(result.changedPaths()).containsExactly("src/App.java",
                "specs/implementation-0a1b2c3d.md");
        assertThat(result.changedFileCount()).isEqualTo(2);

        // The agent received the rendered plan, the feature spec and only existing context files.
        verify(agent).selectFiles(org.mockito.ArgumentMatchers.contains("Add validation"),
                org.mockito.ArgumentMatchers.eq(repoFiles));
        var contextCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        var specCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agent).implement(anyString(), specCaptor.capture(), contextCaptor.capture(), any());
        assertThat(specCaptor.getValue()).isEqualTo("# Feature spec 1");
        assertThat(contextCaptor.getValue()).containsOnlyKeys("src/App.java");
    }

    @Test
    void realiseUsesExistingImplementationPlanFromTheRepository() {
        repoFiles = List.of("src/App.java", "specs/spec-1.md",
                "specs/implementation-00000001.md", "specs/implementation-00000002.md");
        repoContents.put("specs/implementation-00000001.md", "Plan for spec-1.md: add validation");
        repoContents.put("specs/implementation-00000002.md", "Plan for another spec");

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(result.message()).contains("based on existing implementation plan")
                .contains("1 files");
        // Newest candidate read first; the second one references the feature spec and wins.
        assertThat(callsFor("git_read_file").get(0).payload())
                .containsEntry("path", "specs/implementation-00000002.md");
        assertThat(callsFor("git_read_file").get(1).payload())
                .containsEntry("path", "specs/implementation-00000001.md");
        // No extra implementation-plan file in the branch.
        assertThat(callsFor("git_write_file")).hasSize(1);
        verify(agent).selectFiles(org.mockito.ArgumentMatchers.contains("Plan for spec-1.md"), anyList());
    }

    @Test
    void realiseAcceptsFirstReadablePlanWhenNoFeatureSpecSelected() {
        // Newest candidate (00000002) is unreadable and is skipped; 00000001 wins.
        repoFiles = List.of("specs/implementation-00000001.md", "specs/implementation-00000002.md", "src/App.java");
        repoContents.put("specs/implementation-00000001.md", "Arbitrary plan");

        var result = service.realise(RUN_ID, request("  ", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        // Without a feature spec, the commit label is the run.
        assertThat((String) callsFor("git_commit").get(0).payload().get("message"))
                .contains("Implement run 0a1b2c3d");
        verify(agent).selectFiles(org.mockito.ArgumentMatchers.contains("Arbitrary plan"), anyList());
    }

    @Test
    void realiseFallsBackToPipelinePlanWhenCandidateDoesNotMatch() {
        repoFiles = List.of("specs/implementation-00000009.md", "src/App.java");
        repoContents.put("specs/implementation-00000009.md", "Plan for something completely different");

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(result.message()).contains("including new implementation plan");
        verify(agent).selectFiles(org.mockito.ArgumentMatchers.contains("Add validation"), anyList());
    }

    @Test
    void realiseFailsWhenBranchOrListingFails() {
        failures.put("git_create_branch", new GitToolOutcome(false, "clone broken", null, null, null));
        var branchFailure = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());
        assertThat(branchFailure.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(branchFailure.message()).contains("Failed to create branch").contains("clone broken");

        failures.clear();
        failures.put("git_list_files", new GitToolOutcome(false, "list broken", null, null, null));
        var listFailure = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());
        assertThat(listFailure.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(listFailure.message()).contains("Failed to retrieve repository contents");
    }

    @Test
    void realiseFailsWhenAgentProducesNoUsableChanges() {
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("nothing", List.of(
                        new FileChange("../outside-repo.txt", "x"),
                        new FileChange(".git/config", "x"),
                        new FileChange("  ", "x"))));

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        // Deliberate delta (architecture §13.3 item 2): no usable changes is NO_CHANGE, not a
        // publication failure — via toPublication() the emitted AgentEvent for an ungated run
        // (status "FAILED") is unchanged, so this is invisible without a gate.
        assertThat(result.outcome()).isEqualTo(StageOutcome.NO_CHANGE);
        assertThat(result.message()).contains("no implementable file changes");
        assertThat(result.toPublication().status()).isEqualTo("FAILED");
    }

    @Test
    void realiseHandlesNullSelectionAndNullChangeList() {
        when(agent.selectFiles(anyString(), anyList())).thenReturn(new FileSelection(null));
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenReturn(new CodeChangeSet("nothing", null));

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.NO_CHANGE);
        var implementContext = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(agent).implement(anyString(), any(), implementContext.capture(), any());
        assertThat(implementContext.getValue()).isEmpty();
    }

    @Test
    void realiseFailsWhenWriteCommitOrPushFails() {
        failures.put("git_write_file", new GitToolOutcome(false, "disk full", null, null, null));
        var writeFailure = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());
        assertThat(writeFailure.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(writeFailure.message()).contains("Failed to write change (src/App.java)");

        failures.clear();
        failures.put("git_commit", new GitToolOutcome(false, "nothing to commit, working tree clean", null, null, null));
        var commitFailure = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());
        assertThat(commitFailure.message()).contains("Commit failed");
        // "nothing to commit, working tree clean" does not match git-mcp-server's exact pinned wording, so this stays
        // a genuine publication failure — see GitCommitOutcomeClassifierTest for the exact-match case.
        assertThat(commitFailure.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);

        failures.clear();
        failures.put("git_push", new GitToolOutcome(false, "no write permissions", null, null, null));
        var pushFailure = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());
        assertThat(pushFailure.message()).contains("Push failed");
        assertThat(pushFailure.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
    }

    @Test
    void realiseClassifiesGitMcpServersExactNothingToCommitWordingAsNoChange() {
        // AC-27/AC-62 (ADR-006): the one place this exact literal matters — see
        // GitCommitOutcomeClassifierTest for the pinned-literal guard itself.
        failures.put("git_commit", new GitToolOutcome(false,
                "Nothing to commit: workspace has no uncommitted changes", null, null, null));

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.NO_CHANGE);
        assertThat(result.message()).contains("Commit failed");
    }

    @Test
    void realiseStillSucceedsWithCompareUrlWhenPullRequestFails() {
        failures.put("git_create_pull_request", new GitToolOutcome(false, "token missing", null, null, null));

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(result.pullRequestUrl()).isNull();
        assertThat(result.message()).contains("failed to create pull request")
                .contains("https://github.com/org/repo/compare/" + BRANCH + "?expand=1");
    }

    @Test
    void realiseFailsWhenAgentThrows() {
        when(agent.implement(anyString(), any(), anyMap(), any()))
                .thenThrow(new IllegalStateException("llm broken"));

        var result = service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        assertThat(result.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(result.message()).contains("Realisation failed").contains("llm broken");
    }

    @Test
    void featureSpecFallsBackToReferenceTextWhenNotInRepository() {
        repoFiles = List.of("src/App.java");

        service.realise(RUN_ID, request("create a login page", REPO_URL), plan, tests, review, RealisationIteration.first());

        var specCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agent).implement(anyString(), specCaptor.capture(), anyMap(), any());
        assertThat(specCaptor.getValue()).isEqualTo("create a login page");
    }

    @Test
    void featureSpecReadFailureFallsBackToReferenceText() {
        repoFiles = List.of("specs/spec-1.md", "src/App.java");
        repoContents.remove("specs/spec-1.md");

        service.realise(RUN_ID, request("spec-1.md", REPO_URL), plan, tests, review, RealisationIteration.first());

        var specCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agent).implement(anyString(), specCaptor.capture(), anyMap(), any());
        assertThat(specCaptor.getValue()).isEqualTo("spec-1.md");
    }

    @Test
    void safePathAndShortLabelHelpers() {
        assertThat(CodeRealisationService.isSafePath("src/App.java")).isTrue();
        assertThat(CodeRealisationService.isSafePath(null)).isFalse();
        assertThat(CodeRealisationService.isSafePath("  ")).isFalse();
        assertThat(CodeRealisationService.isSafePath("/etc/passwd")).isFalse();
        assertThat(CodeRealisationService.isSafePath("a/../b")).isFalse();
        assertThat(CodeRealisationService.isSafePath(".git")).isFalse();
        assertThat(CodeRealisationService.isSafePath(".git/config")).isFalse();

        assertThat(CodeRealisationService.shortLabel("specs/spec-1.md")).isEqualTo("spec-1.md");
        assertThat(CodeRealisationService.shortLabel(" spec-1.md ")).isEqualTo("spec-1.md");
        assertThat(CodeRealisationService.shortLabel("x".repeat(80))).hasSize(60);
    }

    // ── MADP-54 : base branch threading, iteration 1 (AC-03/AC-04/AC-12/AC-14, Q4) ──

    @Test
    void realiseThreadsResolvedProjectBaseBranchIntoBothGitCallsWithTheSameValue() {
        service.realise(RUN_ID, requestWithBase(REPO_URL, "develop"), plan, tests, review,
                RealisationIteration.first());

        var createBase = callsFor("git_create_branch").get(0).payload().get("baseBranch");
        var prBase = callsFor("git_create_pull_request").get(0).payload().get("baseBranch");
        assertThat(createBase).isEqualTo("develop");
        assertThat(prBase).isEqualTo("develop");
        assertThat(createBase).isEqualTo(prBase);
    }

    @Test
    void realiseFallbackBaseBranchPayloadsMatchThePreFixValues() {
        service.realise(RUN_ID, requestWithBase(REPO_URL, null), plan, tests, review,
                RealisationIteration.first());

        assertThat(callsFor("git_create_branch").get(0).payload()).isEqualTo(Map.of(
                "repositoryUrl", REPO_URL,
                "baseBranch", "main",
                "newBranch", BRANCH,
                "username", "bot",
                "token", "secret"));
        assertThat(callsFor("git_create_pull_request").get(0).payload().get("baseBranch")).isEqualTo("main");
    }

    @Test
    void realiseFailsReadablyAndCallsNoGitToolWhenNoBaseBranchResolvable() {
        properties.setBaseBranch("");

        var report = service.realise(RUN_ID, requestWithBase(REPO_URL, "   "), plan, tests, review,
                RealisationIteration.first());

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(report.message()).contains("No base branch determined");
        verifyNoInteractions(git, agent);
    }

    @Test
    void realiseBranchCreationFailureNamesTheAttemptedBaseAndItsOrigin() {
        failures.put("git_create_branch",
                new GitToolOutcome(false, "Ref develop cannot be resolved", null, null, null));

        var report = service.realise(RUN_ID, requestWithBase(REPO_URL, "develop"), plan, tests, review,
                RealisationIteration.first());

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(report.message())
                .contains("from base branch 'develop'")
                .contains("(project setting)")
                .contains("Ref develop cannot be resolved");
    }

    @Test
    void realiseEmitsExactlyOneBaseBranchInfoLineForIterationOne() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CodeRealisationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.realise(RUN_ID, requestWithBase(REPO_URL, "develop"), plan, tests, review,
                    RealisationIteration.first());
        } finally {
            logger.detachAppender(appender);
        }

        var baseLines = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("uses base branch"))
                .toList();
        assertThat(baseLines).hasSize(1);
        assertThat(baseLines.get(0).getFormattedMessage())
                .contains("develop").contains("(project setting)").doesNotContain("secret");
    }
}
