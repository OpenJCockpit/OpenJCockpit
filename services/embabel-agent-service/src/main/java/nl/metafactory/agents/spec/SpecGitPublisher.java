package nl.metafactory.agents.spec;

import nl.metafactory.agents.domain.ImplementationPlan;
import nl.metafactory.agents.domain.RequirementAnalysis;
import nl.metafactory.agents.domain.ReviewReport;
import nl.metafactory.agents.domain.TestPlan;
import nl.metafactory.agents.model.AgentRunRequest;
import nl.metafactory.agents.spec.GitToolClient.GitToolOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Publishes run outcomes deterministically to git via the git-mcp-server:
 * the spec from the requirement phase and the implementation plan from the
 * implementation/review phase. Each publication clones the project repository
 * (via git_create_branch), creates a branch with the workflow and run id,
 * writes the file, commits, pushes and opens a pull request. This happens
 * in Java code — not via LLM prompt instructions — so that every run demonstrably
 * ends up in git. All tool calls go through the PolicyGuardedMcpToolGateway,
 * so OPA policies remain in effect.
 *
 * <p>Credentials: the project credentials passed per run (from the
 * ai-control-service) take precedence over the static {@code metafactory.spec-git}
 * configuration, so that every push and pull request uses the credentials of the
 * active project.
 */
@Service
public class SpecGitPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpecGitPublisher.class);
    private static final String WORKFLOW_PREFIX = "workflow:";

    private final GitToolClient git;
    private final SpecGitProperties properties;

    public SpecGitPublisher(GitToolClient git, SpecGitProperties properties) {
        this.git = git;
        this.properties = properties;
    }

    /** Publishes the spec from the requirement phase to a spec/ branch with a pull request. */
    public SpecPublication publish(String runId, AgentRunRequest request, RequirementAnalysis analysis) {
        String workflowId = workflowId(request.requestedBy());
        String specPath = properties.getSpecDirectory() + "/" + specFileName(runId);
        return publishFile(runId, request,
                branchName(properties.getBranchPrefix(), workflowId, runId),
                "Spec", specPath,
                renderSpec(workflowId, runId, analysis),
                commitMessage("Add spec", workflowId, runId, specPath),
                "Add spec " + specFileName(runId),
                pullRequestBody("Spec created by run " + runId, analysis.summary()));
    }

    /**
     * Publishes the implementation plan (plus test plan and review findings) from the
     * implementation phase to an impl/ branch with a pull request.
     */
    public SpecPublication publishImplementation(String runId, AgentRunRequest request,
                                                 ImplementationPlan plan, TestPlan tests, ReviewReport review) {
        String workflowId = workflowId(request.requestedBy());
        String filePath = properties.getSpecDirectory() + "/" + implementationFileName(runId);
        String specId = plan.specId() != null && !plan.specId().isBlank()
                ? plan.specId() : "run " + shortRunId(runId);
        return publishFile(runId, request,
                branchName(properties.getImplBranchPrefix(), workflowId, runId),
                "Implementation plan", filePath,
                renderImplementation(workflowId, runId, plan, tests, review),
                commitMessage("Add implementation plan", workflowId, runId, filePath),
                "Implement " + specId,
                pullRequestBody("Implementation plan created by run " + runId, plan.architectureDecision()));
    }

    private SpecPublication publishFile(String runId, AgentRunRequest request, String branch,
                                        String label, String filePath, String content,
                                        String commitMsg, String prTitle, String prBody) {
        if (!properties.isEnabled()) {
            return SpecPublication.skipped(
                    "Spec git publication is disabled (metafactory.spec-git.enabled=false)");
        }
        String repositoryUrl = request.repositoryUrl();
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return SpecPublication.skipped(
                    "No repository URL configured for this workflow — " + label.toLowerCase()
                            + " not published to git");
        }

        String workflowId = workflowId(request.requestedBy());
        String customerId = request.customerId();
        String username = firstNonBlank(request.gitUsername(), properties.getUsername());
        String token = firstNonBlank(request.gitToken(), properties.getToken());
        // MADP-54 BR-2/BR-3/BR-4: resolve the base branch ONCE per stage and reuse the same
        // local variable for git_create_branch and git_create_pull_request, so the branch-off
        // base and the PR target can never diverge.
        String baseBranch = baseBranch(request, properties);
        if (baseBranch == null) {
            return SpecPublication.failed(branch,
                    "No base branch determined: the project has no branch configured and "
                            + "metafactory.spec-git.base-branch is empty");
        }
        log.info("{} publication for run {} uses base branch {} ({})",
                label, runId, baseBranch, baseBranchOrigin(request));
        try {
            GitToolOutcome created = git.call(workflowId, runId, customerId, "git_create_branch", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "baseBranch", baseBranch,
                    "newBranch", branch,
                    "username", username,
                    "token", token));
            if (!created.success()) {
                return SpecPublication.failed(branch, "Failed to create branch from base branch '"
                        + baseBranch + "' (" + baseBranchOrigin(request) + "): " + created.message());
            }

            GitToolOutcome written = git.call(workflowId, runId, customerId, "git_write_file", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "path", filePath,
                    "content", content));
            if (!written.success()) {
                return SpecPublication.failed(branch, label + " write failed: " + written.message());
            }

            GitToolOutcome committed = git.call(workflowId, runId, customerId, "git_commit", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "message", commitMsg,
                    "authorName", properties.getAuthorName(),
                    "authorEmail", properties.getAuthorEmail()));
            if (!committed.success()) {
                return SpecPublication.failed(branch, "Commit failed: " + committed.message());
            }

            GitToolOutcome pushed = git.call(workflowId, runId, customerId, "git_push", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "branch", branch,
                    "username", username,
                    "token", token));
            if (!pushed.success()) {
                return SpecPublication.failed(branch, "Push failed: " + pushed.message());
            }

            GitToolOutcome pullRequest = git.call(workflowId, runId, customerId, "git_create_pull_request", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "baseBranch", baseBranch,
                    "headBranch", branch,
                    "title", prTitle,
                    "body", prBody,
                    "token", token));
            String base = label + " " + filePath + " pushed to branch " + branch;
            if (pullRequest.success() && pullRequest.url() != null) {
                log.info("{} published to branch {} with pull request {} for run {}",
                        label, branch, pullRequest.url(), runId);
                return SpecPublication.published(branch, pullRequest.url(),
                        base + " — pull request: " + pullRequest.url());
            }
            String compare = compareUrl(repositoryUrl, branch);
            String manual = compare != null ? "; open manually: " + compare : "";
            log.info("{} published to branch {} for run {} (pull request not created: {})",
                    label, branch, runId, pullRequest.message());
            return SpecPublication.published(branch,
                    base + " (failed to create pull request: " + pullRequest.message() + manual + ")");
        } catch (Exception e) {
            log.warn("Git publication failed for run {}: {}", runId, e.getMessage());
            return SpecPublication.failed(branch, "Git publication failed: " + e.getMessage());
        }
    }

    static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    /**
     * MADP-54 BR-2/BR-4: the per-run request value wins over the configured default; a
     * null/blank value on both sides resolves to {@code null} (never a blank ref handed to
     * {@code Map.of}). Also used by {@link CodeRealisationService}.
     */
    static String baseBranch(AgentRunRequest request, SpecGitProperties properties) {
        String resolved = firstNonBlank(request.baseBranch(), properties.getBaseBranch());
        return (resolved == null || resolved.isBlank()) ? null : resolved;
    }

    /**
     * MADP-54 Q4/AC-14: where the resolved base branch came from, for the INFO line and the
     * branch-creation failure text. Returns a fixed literal — never a secret or user text.
     */
    static String baseBranchOrigin(AgentRunRequest request) {
        return (request.baseBranch() != null && !request.baseBranch().isBlank())
                ? "project setting" : "configured default";
    }

    /** GitHub compare URL as a manual fallback when the PR tool fails. */
    static String compareUrl(String repositoryUrl, String branch) {
        if (repositoryUrl == null || !repositoryUrl.startsWith("http")) {
            return null;
        }
        String base = repositoryUrl.endsWith(".git")
                ? repositoryUrl.substring(0, repositoryUrl.length() - 4) : repositoryUrl;
        return base + "/compare/" + branch + "?expand=1";
    }

    /** The workflow id from requestedBy ("workflow:&lt;id&gt;"); null for other starters. */
    static String workflowId(String requestedBy) {
        if (requestedBy != null && requestedBy.startsWith(WORKFLOW_PREFIX)
                && requestedBy.length() > WORKFLOW_PREFIX.length()) {
            return requestedBy.substring(WORKFLOW_PREFIX.length());
        }
        return null;
    }

    String branchName(String prefix, String workflowId, String runId) {
        String middle = workflowId != null ? sanitize(workflowId) + "-" : "";
        return prefix + "/" + middle + shortRunId(runId);
    }

    static String specFileName(String runId) {
        return "spec-" + shortRunId(runId) + ".md";
    }

    static String implementationFileName(String runId) {
        return "implementation-" + shortRunId(runId) + ".md";
    }

    static String shortRunId(String runId) {
        return runId.length() > 8 ? runId.substring(0, 8) : runId;
    }

    static String sanitize(String value) {
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "workflow" : slug;
    }

    static String renderSpec(String workflowId, String runId, RequirementAnalysis analysis) {
        StringBuilder md = new StringBuilder();
        appendFrontmatter(md, "spec-" + shortRunId(runId), workflowId, runId);
        String summary = analysis.summary() != null ? analysis.summary() : "Spec " + runId;
        md.append("# ").append(summary).append("\n\n");
        md.append("## Requirements\n\n");
        for (String requirement : orEmpty(analysis.requirements())) {
            md.append("- ").append(requirement).append("\n");
        }
        return md.toString();
    }

    static String renderImplementation(String workflowId, String runId,
                                       ImplementationPlan plan, TestPlan tests, ReviewReport review) {
        StringBuilder md = new StringBuilder();
        appendFrontmatter(md, "implementation-" + shortRunId(runId), workflowId, runId);
        md.append("# Implementation plan");
        if (plan.specId() != null && !plan.specId().isBlank()) {
            md.append(" for ").append(plan.specId());
        }
        md.append("\n\n");
        md.append("## Architecture decision\n\n")
          .append(plan.architectureDecision() != null ? plan.architectureDecision() : "n/a")
          .append("\n\n");
        md.append("## Proposed changes\n\n");
        for (String change : orEmpty(plan.proposedChanges())) {
            md.append("- ").append(change).append("\n");
        }
        md.append("\n## Test plan");
        if (tests.coverageTarget() != null && !tests.coverageTarget().isBlank()) {
            md.append(" (coverage target: ").append(tests.coverageTarget()).append(")");
        }
        md.append("\n\n");
        for (String testCase : orEmpty(tests.testCases())) {
            md.append("- ").append(testCase).append("\n");
        }
        md.append("\n## Review\n\n");
        md.append("Approved: ").append(review.approved() ? "yes" : "no").append("\n\n");
        for (String finding : orEmpty(review.findings())) {
            md.append("- ").append(finding).append("\n");
        }
        return md.toString();
    }

    private static void appendFrontmatter(StringBuilder md, String id, String workflowId, String runId) {
        md.append("---\n");
        md.append("id: ").append(id).append("\n");
        md.append("workflow_id: ").append(workflowId != null ? workflowId : "unknown").append("\n");
        md.append("run_id: ").append(runId).append("\n");
        md.append("status: draft\n");
        md.append("created_at: ").append(Instant.now()).append("\n");
        md.append("---\n\n");
    }

    private static List<String> orEmpty(List<String> values) {
        return values != null ? values : List.of();
    }

    static String commitMessage(String action, String workflowId, String runId, String filePath) {
        String workflowSuffix = workflowId != null ? " (workflow " + workflowId + ")" : "";
        return action + " for run " + runId + workflowSuffix + "\n\nFile: " + filePath + "\n";
    }

    static String pullRequestBody(String header, String detail) {
        return detail != null && !detail.isBlank() ? header + "\n\n" + detail : header;
    }
}
