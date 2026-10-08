package nl.metafactory.agents.spec;

import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.policy.mcp.McpToolInvocation;
import nl.metafactory.agents.policy.mcp.McpToolResult;
import nl.metafactory.agents.policy.mcp.PolicyGuardedMcpToolGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpecGitPublisherTest {

    private static final String RUN_ID = "0a1b2c3d-e4f5-6789-abcd-ef0123456789";
    private static final String REPO_URL = "https://github.com/org/repo.git";
    private static final String PR_URL = "https://github.com/org/repo/pull/7";

    private PolicyGuardedMcpToolGateway gateway;
    private SpecGitProperties properties;
    private SpecGitPublisher publisher;
    private final List<McpToolInvocation> invocations = new ArrayList<>();

    private final RequirementAnalysis analysis = new RequirementAnalysis(
            "spec.md", List.of("Frontend runs with mock profile", "README updated"),
            "Frontend mock profile without external dependencies");

    private final ImplementationPlan plan = new ImplementationPlan("spec-384910d8.md",
            List.of("Add validation", "Extend the REST API"),
            "Existing architecture suffices; changes in service and UI layer");
    private final TestPlan tests = new TestPlan("spec-384910d8.md",
            List.of("Validate input", "Test the new endpoints"), "90%");
    private final ReviewReport review = new ReviewReport("spec-384910d8.md", false,
            List.of("Migration plan missing"));

    @BeforeEach
    void setUp() {
        gateway = mock(PolicyGuardedMcpToolGateway.class);
        properties = new SpecGitProperties();
        publisher = new SpecGitPublisher(new GitToolClient(gateway), properties);
        stubAllToolsSucceed();
    }

    private void stubAllToolsSucceed() {
        doAnswer(inv -> {
            McpToolInvocation invocation = inv.getArgument(0);
            invocations.add(invocation);
            if (invocation.toolName().equals("git_create_pull_request")) {
                return new McpToolResult(true, false,
                        "{\"success\":true,\"message\":\"Created pull request\",\"url\":\"" + PR_URL + "\"}", null);
            }
            return new McpToolResult(true, false, "{\"success\":true,\"message\":\"ok\"}", null);
        }).when(gateway).invoke(any());
    }

    private AgentRunRequest request(String requestedBy, String repositoryUrl) {
        return request(requestedBy, repositoryUrl, null, null);
    }

    private AgentRunRequest request(String requestedBy, String repositoryUrl,
                                    String gitUsername, String gitToken) {
        return new AgentRunRequest("cust", "create a spec", List.of("requirement"),
                requestedBy, repositoryUrl, gitUsername, gitToken, null, null, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    private AgentRunRequest requestWithBase(String repositoryUrl, String baseBranch) {
        return new AgentRunRequest("cust", "create a spec", List.of("requirement"),
                "workflow:wf-1", repositoryUrl, null, null, null, baseBranch, null, null,
                nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
    }

    // ── Spec publication ──────────────────────────────────────────────────────

    @Test
    void publishSkipsWhenDisabled() {
        properties.setEnabled(false);

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.isPublished()).isFalse();
        verifyNoInteractions(gateway);
    }

    @Test
    void publishSkipsWithoutRepositoryUrl() {
        var nullUrl = publisher.publish(RUN_ID, request("workflow:wf-1", null), analysis);
        var blankUrl = publisher.publish(RUN_ID, request("workflow:wf-1", "  "), analysis);

        assertThat(nullUrl.status()).isEqualTo("SKIPPED");
        assertThat(nullUrl.message()).contains("No repository URL");
        assertThat(blankUrl.status()).isEqualTo("SKIPPED");
        verifyNoInteractions(gateway);
    }

    @Test
    void publishRunsBranchWriteCommitPushPullRequestInOrder() {
        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.branch()).isEqualTo("spec/wf-1-0a1b2c3d");
        assertThat(result.pullRequestUrl()).isEqualTo(PR_URL);
        assertThat(result.message()).contains("specs/spec-0a1b2c3d.md")
                .contains("spec/wf-1-0a1b2c3d")
                .contains("pull request: " + PR_URL);

        assertThat(invocations).extracting(McpToolInvocation::toolName)
                .containsExactly("git_create_branch", "git_write_file", "git_commit",
                        "git_push", "git_create_pull_request");
        assertThat(invocations).allSatisfy(invocation -> {
            assertThat(invocation.workflowId()).isEqualTo("wf-1");
            assertThat(invocation.workflowExecutionId()).isEqualTo(RUN_ID);
            assertThat(invocation.customerId()).isEqualTo("cust");
            assertThat(invocation.agentId()).isEqualTo("requirement");
            assertThat(invocation.operation()).isEqualTo("spec.publish");
        });

        var createPayload = invocations.get(0).payload();
        assertThat(createPayload).containsEntry("repositoryUrl", REPO_URL)
                .containsEntry("baseBranch", "main")
                .containsEntry("newBranch", "spec/wf-1-0a1b2c3d");

        var writePayload = invocations.get(1).payload();
        assertThat(writePayload).containsEntry("path", "specs/spec-0a1b2c3d.md");
        assertThat((String) writePayload.get("content"))
                .contains("workflow_id: wf-1")
                .contains("run_id: " + RUN_ID)
                .contains("# Frontend mock profile without external dependencies")
                .contains("- Frontend runs with mock profile")
                .contains("- README updated");

        var commitPayload = invocations.get(2).payload();
        assertThat((String) commitPayload.get("message"))
                .contains("Add spec for run " + RUN_ID)
                .contains("workflow wf-1")
                .contains("specs/spec-0a1b2c3d.md");
        assertThat(commitPayload).containsEntry("authorName", "Agentic Workflow")
                .containsEntry("authorEmail", "agentic@metafactory.nl");

        var pushPayload = invocations.get(3).payload();
        assertThat(pushPayload).containsEntry("branch", "spec/wf-1-0a1b2c3d");

        var prPayload = invocations.get(4).payload();
        assertThat(prPayload).containsEntry("baseBranch", "main")
                .containsEntry("headBranch", "spec/wf-1-0a1b2c3d")
                .containsEntry("title", "Add spec spec-0a1b2c3d.md");
        assertThat((String) prPayload.get("body")).contains("Spec created by run " + RUN_ID)
                .contains("Frontend mock profile without external dependencies");
    }

    @Test
    void publishStillSucceedsWithCompareUrlWhenPullRequestCreationFails() {
        failOnTool("git_create_pull_request", "A GitHub token is required to create a pull request");

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.pullRequestUrl()).isNull();
        assertThat(result.message())
                .contains("failed to create pull request")
                .contains("GitHub token is required")
                .contains("https://github.com/org/repo/compare/spec/wf-1-0a1b2c3d?expand=1");
    }

    @Test
    void publishOmitsCompareUrlForNonHttpRepositories() {
        failOnTool("git_create_pull_request", "no API");

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", "file:///tmp/repo.git"), analysis);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.message()).contains("failed to create pull request")
                .doesNotContain("open manually");
    }

    @Test
    void publishUsesRunCredentialsOverConfiguredOnes() {
        properties.setUsername("config-user");
        properties.setToken("config-token");

        publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL, "project-bot", "project-secret"), analysis);

        assertThat(invocations.get(0).payload())
                .containsEntry("username", "project-bot")
                .containsEntry("token", "project-secret");
        assertThat(invocations.get(3).payload())
                .containsEntry("username", "project-bot")
                .containsEntry("token", "project-secret");
        assertThat(invocations.get(4).payload()).containsEntry("token", "project-secret");
    }

    @Test
    void publishFallsBackToConfiguredCredentialsWhenRunHasNone() {
        properties.setUsername("config-user");
        properties.setToken("config-token");

        publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL, null, "  "), analysis);

        assertThat(invocations.get(0).payload())
                .containsEntry("username", "config-user")
                .containsEntry("token", "config-token");
    }

    @Test
    void publishWithoutWorkflowRequesterUsesRunOnlyBranchName() {
        var result = publisher.publish(RUN_ID, request("dashboard-user", REPO_URL), analysis);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.branch()).isEqualTo("spec/0a1b2c3d");
        assertThat(invocations.get(0).workflowId()).isNull();
        assertThat((String) invocations.get(1).payload().get("content")).contains("workflow_id: unknown");
        assertThat((String) invocations.get(2).payload().get("message")).doesNotContain("workflow ");
    }

    @Test
    void publishFailsWhenBranchCreationFails() {
        org.mockito.Mockito.doReturn(new McpToolResult(false, false, "clone broken", null))
                .when(gateway).invoke(any());

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Failed to create branch").contains("clone broken");
    }

    @Test
    void publishFailsOnToolLevelFailureDespiteMcpSuccess() {
        // The git-mcp-server reports errors as GitToolResult JSON while the MCP call itself succeeds.
        org.mockito.Mockito.doReturn(new McpToolResult(true, false,
                "{\"success\":false,\"message\":\"git_create_branch failed: not authorized\","
                        + "\"workspace\":null,\"branch\":null,\"commitHash\":null}", null))
                .when(gateway).invoke(any());

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message())
                .contains("Failed to create branch")
                .contains("git_create_branch failed: not authorized");
    }

    @Test
    void publishFailsWhenWriteFails() {
        failOnTool("git_write_file", "disk full");

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Spec write failed").contains("disk full");
    }

    @Test
    void publishFailsWhenCommitFails() {
        failOnTool("git_commit", "nothing to commit, working tree clean");

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Commit failed");
    }

    @Test
    void publishFailsWhenPushFails() {
        failOnTool("git_push", "no write permissions");

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Push failed").contains("no write permissions");
    }

    @Test
    void publishFailsWhenPolicyBlocksTheToolCall() {
        org.mockito.Mockito.doReturn(McpToolResult.blocked("Restricted project"))
                .when(gateway).invoke(any());

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Restricted project");
    }

    @Test
    void publishFailsWhenGatewayThrows() {
        org.mockito.Mockito.doThrow(new IllegalStateException("gateway broken"))
                .when(gateway).invoke(any());

        var result = publisher.publish(RUN_ID, request("workflow:wf-1", REPO_URL), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Git publication failed").contains("gateway broken");
    }

    // ── Implementation plan publication ─────────────────────────────────────────

    @Test
    void publishImplementationPushesPlanBranchAndOpensPullRequest() {
        var result = publisher.publishImplementation(RUN_ID,
                request("workflow:wf-spec-implement", REPO_URL), plan, tests, review);

        assertThat(result.isPublished()).isTrue();
        assertThat(result.branch()).isEqualTo("impl/wf-spec-implement-0a1b2c3d");
        assertThat(result.pullRequestUrl()).isEqualTo(PR_URL);
        assertThat(result.message()).contains("Implementation plan specs/implementation-0a1b2c3d.md");

        assertThat(invocations).extracting(McpToolInvocation::toolName)
                .containsExactly("git_create_branch", "git_write_file", "git_commit",
                        "git_push", "git_create_pull_request");

        var writePayload = invocations.get(1).payload();
        assertThat(writePayload).containsEntry("path", "specs/implementation-0a1b2c3d.md");
        assertThat((String) writePayload.get("content"))
                .contains("# Implementation plan for spec-384910d8.md")
                .contains("Existing architecture suffices")
                .contains("- Add validation")
                .contains("- Extend the REST API")
                .contains("## Test plan (coverage target: 90%)")
                .contains("- Validate input")
                .contains("Approved: no")
                .contains("- Migration plan missing");

        assertThat((String) invocations.get(2).payload().get("message"))
                .contains("Add implementation plan for run " + RUN_ID)
                .contains("workflow wf-spec-implement");

        var prPayload = invocations.get(4).payload();
        assertThat(prPayload).containsEntry("headBranch", "impl/wf-spec-implement-0a1b2c3d")
                .containsEntry("title", "Implement spec-384910d8.md");
        assertThat((String) prPayload.get("body"))
                .contains("Implementation plan created by run " + RUN_ID)
                .contains("Existing architecture suffices");
    }

    @Test
    void publishImplementationUsesRunIdInTitleWhenSpecIdMissing() {
        var withoutSpecId = new ImplementationPlan("  ", List.of("x"), null);

        publisher.publishImplementation(RUN_ID, request("workflow:wf-1", REPO_URL),
                withoutSpecId, tests, review);

        assertThat(invocations.get(4).payload())
                .containsEntry("title", "Implement run 0a1b2c3d");
    }

    @Test
    void publishImplementationSkipsWithoutRepositoryUrl() {
        var result = publisher.publishImplementation(RUN_ID, request("workflow:wf-1", null),
                plan, tests, review);

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.message()).contains("implementation plan not published to git");
        verifyNoInteractions(gateway);
    }

    @Test
    void publishImplementationFailsWhenWriteFails() {
        failOnTool("git_write_file", "disk full");

        var result = publisher.publishImplementation(RUN_ID, request("workflow:wf-1", REPO_URL),
                plan, tests, review);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Implementation plan write failed");
    }

    // ── Helpers en rendering ─────────────────────────────────────────────────

    @Test
    void renderSpecHandlesMissingSummaryAndRequirements() {
        var empty = new RequirementAnalysis("spec.md", null, null);

        String markdown = SpecGitPublisher.renderSpec("wf-1", RUN_ID, empty);

        assertThat(markdown).contains("# Spec " + RUN_ID).contains("## Requirements");
    }

    @Test
    void renderImplementationHandlesMissingFields() {
        var emptyPlan = new ImplementationPlan(null, null, null);
        var emptyTests = new TestPlan(null, null, "  ");
        var approvedReview = new ReviewReport(null, true, null);

        String markdown = SpecGitPublisher.renderImplementation(null, RUN_ID,
                emptyPlan, emptyTests, approvedReview);

        assertThat(markdown).contains("# Implementation plan\n")
                .contains("workflow_id: unknown")
                .contains("n/a")
                .contains("## Test plan\n")
                .contains("Approved: yes");
    }

    @Test
    void compareUrlIsOnlyBuiltForHttpUrls() {
        assertThat(SpecGitPublisher.compareUrl(REPO_URL, "spec/x"))
                .isEqualTo("https://github.com/org/repo/compare/spec/x?expand=1");
        assertThat(SpecGitPublisher.compareUrl("https://github.com/org/repo", "spec/x"))
                .isEqualTo("https://github.com/org/repo/compare/spec/x?expand=1");
        assertThat(SpecGitPublisher.compareUrl("file:///tmp/repo.git", "spec/x")).isNull();
        assertThat(SpecGitPublisher.compareUrl(null, "spec/x")).isNull();
    }

    @Test
    void workflowIdIsOnlyExtractedFromWorkflowRequesters() {
        assertThat(SpecGitPublisher.workflowId("workflow:wf-9")).isEqualTo("wf-9");
        assertThat(SpecGitPublisher.workflowId("workflow:")).isNull();
        assertThat(SpecGitPublisher.workflowId("user")).isNull();
        assertThat(SpecGitPublisher.workflowId(null)).isNull();
    }

    @Test
    void branchNamesAreSanitizedAndShortRunIdsAreKept() {
        assertThat(SpecGitPublisher.sanitize("WF 001/Ë!")).isEqualTo("wf-001");
        assertThat(SpecGitPublisher.sanitize("!!!")).isEqualTo("workflow");
        assertThat(SpecGitPublisher.shortRunId("abc")).isEqualTo("abc");

        var result = publisher.publish("abc", request("workflow:WF 001", REPO_URL), analysis);
        assertThat(result.branch()).isEqualTo("spec/wf-001-abc");
    }

    @Test
    void pullRequestBodyOmitsBlankDetail() {
        assertThat(SpecGitPublisher.pullRequestBody("Header", "Detail")).isEqualTo("Header\n\nDetail");
        assertThat(SpecGitPublisher.pullRequestBody("Header", "  ")).isEqualTo("Header");
        assertThat(SpecGitPublisher.pullRequestBody("Header", null)).isEqualTo("Header");
    }

    @Test
    void specPublicationFactoriesExposeStatus() {
        assertThat(SpecPublication.published("b", "m").isPublished()).isTrue();
        assertThat(SpecPublication.published("b", "m").pullRequestUrl()).isNull();
        assertThat(SpecPublication.published("b", PR_URL, "m").pullRequestUrl()).isEqualTo(PR_URL);
        assertThat(SpecPublication.skipped("m").branch()).isNull();
        assertThat(SpecPublication.failed("b", "m").isPublished()).isFalse();
    }

    // ── MADP-54 : base branch threading (AC-01/AC-02/AC-04/AC-12/AC-14, Q4) ──────

    @Test
    void publishThreadsResolvedBaseBranchIntoBothGitCallsWithTheSameValue() {
        publisher.publish(RUN_ID, requestWithBase(REPO_URL, "develop"), analysis);

        var createPayload = invocations.get(0).payload();
        var prPayload = invocations.get(4).payload();
        assertThat(createPayload).containsEntry("baseBranch", "develop");
        assertThat(prPayload).containsEntry("baseBranch", "develop");
        assertThat(createPayload.get("baseBranch")).isEqualTo(prPayload.get("baseBranch"));
    }

    @Test
    void publishImplementationThreadsResolvedBaseBranchIntoBothGitCalls() {
        publisher.publishImplementation(RUN_ID, requestWithBase(REPO_URL, "develop"), plan, tests, review);

        assertThat(invocations.get(0).payload()).containsEntry("baseBranch", "develop");
        assertThat(invocations.get(4).payload()).containsEntry("baseBranch", "develop");
        assertThat(invocations.get(0).toolName()).isEqualTo("git_create_branch");
        assertThat(invocations.get(4).toolName()).isEqualTo("git_create_pull_request");
    }

    @Test
    void publishFallbackBaseBranchPayloadsEqualThePreFixMapsKeyForKey() {
        // request carries no base → the configured default ("main") is used and every payload
        // key equals what the pre-MADP-54 code produced.
        publisher.publish(RUN_ID, requestWithBase(REPO_URL, null), analysis);

        assertThat(invocations.get(0).payload()).isEqualTo(Map.of(
                "repositoryUrl", REPO_URL,
                "baseBranch", "main",
                "newBranch", "spec/wf-1-0a1b2c3d",
                "username", "",
                "token", "",
                "workspaceKey", RUN_ID));
        assertThat(invocations.get(4).payload()).isEqualTo(Map.of(
                "repositoryUrl", REPO_URL,
                "baseBranch", "main",
                "headBranch", "spec/wf-1-0a1b2c3d",
                "title", "Add spec spec-0a1b2c3d.md",
                "body", invocations.get(4).payload().get("body"),
                "token", "",
                "workspaceKey", RUN_ID));
    }

    @Test
    void publishFailsReadablyAndCallsNoGitToolWhenNoBaseBranchResolvable() {
        properties.setBaseBranch("");

        var result = publisher.publish(RUN_ID, requestWithBase(REPO_URL, "  "), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("No base branch determined");
        verifyNoInteractions(gateway);
    }

    @Test
    void publishBranchCreationFailureNamesTheAttemptedBaseBranchAndItsOrigin() {
        failOnTool("git_create_branch", "Ref develop cannot be resolved");

        var result = publisher.publish(RUN_ID, requestWithBase(REPO_URL, "develop"), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message())
                .contains("from base branch 'develop'")
                .contains("(project setting)")
                .contains("Ref develop cannot be resolved");
    }

    @Test
    void publishBranchCreationFailureLabelsAConfiguredDefaultOrigin() {
        failOnTool("git_create_branch", "boom");

        var result = publisher.publish(RUN_ID, requestWithBase(REPO_URL, null), analysis);

        assertThat(result.message()).contains("from base branch 'main'").contains("(configured default)");
    }

    @Test
    void publishEmitsExactlyOneBaseBranchInfoLinePerPublicationWithoutSecrets() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SpecGitPublisher.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            publisher.publish(RUN_ID, requestWithBase(REPO_URL, "develop"), analysis);
        } finally {
            logger.detachAppender(appender);
        }

        var baseLines = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("uses base branch"))
                .toList();
        assertThat(baseLines).hasSize(1);
        assertThat(baseLines.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
        assertThat(baseLines.get(0).getFormattedMessage())
                .contains("develop").contains("(project setting)")
                .doesNotContain("secret").doesNotContain("token");
    }

    @Test
    void publishFailsWithoutBaseBranchWhenConfiguredBaseBranchIsLiterallyNull() {
        // baseBranch(): resolved == null true-edge of (resolved == null || resolved.isBlank())
        properties.setBaseBranch(null);

        var result = publisher.publish(RUN_ID, requestWithBase(REPO_URL, null), analysis);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("No base branch determined");
        verifyNoInteractions(gateway);
    }

    @Test
    void publishLabelsConfiguredDefaultOriginWhenRequestBaseBranchIsBlankNotNull() {
        // baseBranchOrigin(): request.baseBranch() != null true, !isBlank() false -> "configured default"
        failOnTool("git_create_branch", "boom");

        var result = publisher.publish(RUN_ID, requestWithBase(REPO_URL, "   "), analysis);

        assertThat(result.message())
                .contains("from base branch 'main'")
                .contains("(configured default)");
    }

    private void failOnTool(String toolName, String message) {
        doAnswer(inv -> {
            McpToolInvocation invocation = inv.getArgument(0);
            invocations.add(invocation);
            if (invocation.toolName().equals(toolName)) {
                return new McpToolResult(true, false,
                        "{\"success\":false,\"message\":\"" + message + "\"}", null);
            }
            if (invocation.toolName().equals("git_create_pull_request")) {
                return new McpToolResult(true, false,
                        "{\"success\":true,\"message\":\"Created pull request\",\"url\":\"" + PR_URL + "\"}", null);
            }
            return new McpToolResult(true, false, "{\"success\":true,\"message\":\"ok\"}", null);
        }).when(gateway).invoke(any());
    }
}
