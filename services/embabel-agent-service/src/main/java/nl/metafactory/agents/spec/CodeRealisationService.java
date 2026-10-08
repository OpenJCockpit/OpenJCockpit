package nl.metafactory.agents.spec;

import nl.metafactory.agents.approval.GitCommitOutcomeClassifier;
import nl.metafactory.agents.approval.StageChangeReports;
import nl.metafactory.agents.approval.model.StageChangeReport;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actually applies an implementation plan in the project repository: clones
 * the repository, looks for an existing implementation plan in specs/ for the
 * selected feature spec (or uses the plan the pipeline created in the same
 * run — the "implement spec" phase then runs as preparation),
 * has the realisation subagent generate code changes based on the actual
 * repository contents, and pushes them as a feature branch with a pull request.
 * All git calls go through the policy-gated GitToolClient.
 */
@Service
public class CodeRealisationService {

    private static final Logger log = LoggerFactory.getLogger(CodeRealisationService.class);
    static final int MAX_CONTEXT_FILES = 10;
    static final int MAX_EXISTING_PLAN_CANDIDATES = 5;

    private final GitToolClient git;
    private final SpecGitProperties properties;
    private final CodeRealisationAgent agent;

    public CodeRealisationService(GitToolClient git, SpecGitProperties properties,
                                  CodeRealisationAgent agent) {
        this.git = git;
        this.properties = properties;
        this.agent = agent;
    }

    public StageChangeReport realise(String runId, AgentRunRequest request,
                                     ImplementationPlan plan, TestPlan tests, ReviewReport review,
                                     RealisationIteration iteration) {
        if (!properties.isEnabled()) {
            return StageChangeReports.notApplicable(
                    "Spec git publication is disabled (metafactory.spec-git.enabled=false)");
        }
        String repositoryUrl = request.repositoryUrl();
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return StageChangeReports.notApplicable(
                    "No repository URL configured for this workflow — implementation not performed");
        }

        String workflowId = SpecGitPublisher.workflowId(request.requestedBy());
        String customerId = request.customerId();
        String username = SpecGitPublisher.firstNonBlank(request.gitUsername(), properties.getUsername());
        String token = SpecGitPublisher.firstNonBlank(request.gitToken(), properties.getToken());
        String branch = properties.getRealisationBranchPrefix() + "/"
                + (workflowId != null ? SpecGitPublisher.sanitize(workflowId) + "-" : "")
                + SpecGitPublisher.shortRunId(runId);
        // MADP-54 BR-2/BR-3/BR-4: resolve the base branch ONCE and reuse the same local
        // variable for git_create_branch and git_create_pull_request. The guard sits before the
        // iteration split so a blank resolved base never reaches Map.of on any path.
        String baseBranch = SpecGitPublisher.baseBranch(request, properties);
        if (baseBranch == null) {
            return StageChangeReports.publishFailed(branch,
                    "No base branch determined: the project has no branch configured and "
                            + "metafactory.spec-git.base-branch is empty");
        }
        if (iteration.isFirst()) {
            log.info("Realisation for run {} uses base branch {} ({})",
                    runId, baseBranch, SpecGitPublisher.baseBranchOrigin(request));
        }
        try {
            GitToolOutcome created;
            if (iteration.isFirst()) {
                created = git.call(workflowId, runId, customerId, "git_create_branch", Map.of(
                        "repositoryUrl", repositoryUrl,
                        "baseBranch", baseBranch,
                        "newBranch", branch,
                        "username", username,
                        "token", token));
                if (!created.success()) {
                    return StageChangeReports.publishFailed(branch, "Failed to create branch from base branch '"
                            + baseBranch + "' (" + SpecGitPublisher.baseBranchOrigin(request) + "): "
                            + created.message());
                }
            } else {
                // V2/R2 — the single most likely implementation trap: git_create_branch maps to
                // checkout --create and JGit throws RefAlreadyExistsException because
                // git-mcp-server keeps one persistent workspace clone per repository, and the
                // branch from iteration 1 is still there. git_checkout_branch is idempotent and
                // needs no new MCP tool (see GitToolsService.java:39-55 vs :57-77).
                created = git.call(workflowId, runId, customerId, "git_checkout_branch", Map.of(
                        "repositoryUrl", repositoryUrl,
                        "branch", branch,
                        "username", username,
                        "token", token));
                if (!created.success()) {
                    return StageChangeReports.publishFailed(branch, "Failed to switch branch: " + created.message());
                }
            }

            GitToolOutcome listed = git.call(workflowId, runId, customerId, "git_list_files", Map.of(
                    "repositoryUrl", repositoryUrl));
            if (!listed.success() || listed.files() == null) {
                return StageChangeReports.publishFailed(branch, "Failed to retrieve repository contents: " + listed.message());
            }
            List<String> repositoryFiles = listed.files();

            // Existing implementation plan for the selected feature spec, or the plan from this run.
            String featureSpecRef = request.specFile();
            String planMarkdown = findExistingImplementationSpec(workflowId, runId, customerId,
                    repositoryUrl, repositoryFiles, featureSpecRef);
            boolean planFromRepo = planMarkdown != null;
            if (!planFromRepo) {
                planMarkdown = SpecGitPublisher.renderImplementation(workflowId, runId, plan, tests, review);
            }

            String featureSpecContext = readFeatureSpec(workflowId, runId, customerId,
                    repositoryUrl, repositoryFiles, featureSpecRef);

            FileSelection selection = agent.selectFiles(planMarkdown, repositoryFiles);
            Map<String, String> contextFiles = readContextFiles(workflowId, runId, customerId,
                    repositoryUrl, repositoryFiles, selection);

            ReviewerFeedback feedback = iteration.isFirst() ? null
                    : new ReviewerFeedback(iteration.number(), iteration.feedback(), iteration.previousChangedPaths());
            CodeChangeSet changeSet = agent.implement(planMarkdown, featureSpecContext, contextFiles, feedback);
            List<FileChange> changes = validChanges(changeSet);
            if (changes.isEmpty()) {
                // Deliberate delta (architecture §13.3 item 2): an agent producing no implementable
                // change is NO_CHANGE, not a publication failure — via toPublication() the emitted
                // AgentEvent for an ungated run is unchanged, so this is invisible without a gate.
                return StageChangeReports.noChange(branch,
                        "Realisation agent produced no implementable file changes");
            }

            if (!planFromRepo) {
                changes = new ArrayList<>(changes);
                changes.add(new FileChange(properties.getSpecDirectory() + "/"
                        + SpecGitPublisher.implementationFileName(runId), planMarkdown));
            }
            for (FileChange change : changes) {
                GitToolOutcome written = git.call(workflowId, runId, customerId, "git_write_file", Map.of(
                        "repositoryUrl", repositoryUrl,
                        "path", change.path(),
                        "content", change.content() != null ? change.content() : ""));
                if (!written.success()) {
                    return StageChangeReports.publishFailed(branch,
                            "Failed to write change (" + change.path() + "): " + written.message());
                }
            }

            String specLabel = featureSpecRef != null && !featureSpecRef.isBlank()
                    ? shortLabel(featureSpecRef) : "run " + SpecGitPublisher.shortRunId(runId);
            // The commit message gains only the iteration number — a bounded integer, never
            // operator text (mitigates risk 9 without touching risk 4/AC-41). Iteration 1's
            // message is unchanged, so an ungated or first-pass run is byte-identical (AC-04).
            String implementLabel = iteration.isFirst() ? ("Implement " + specLabel)
                    : ("Implement " + specLabel + " (review iteration " + iteration.number() + ")");
            GitToolOutcome committed = git.call(workflowId, runId, customerId, "git_commit", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "message", SpecGitPublisher.commitMessage(implementLabel,
                            workflowId, runId, changes.size() + " changed files"),
                    "authorName", properties.getAuthorName(),
                    "authorEmail", properties.getAuthorEmail()));
            if (!committed.success()) {
                String commitFailureMessage = "Commit failed: " + committed.message();
                return GitCommitOutcomeClassifier.isNothingToCommit(committed.message())
                        ? StageChangeReports.noChange(branch, commitFailureMessage)
                        : StageChangeReports.publishFailed(branch, commitFailureMessage);
            }

            GitToolOutcome pushed = git.call(workflowId, runId, customerId, "git_push", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "branch", branch,
                    "username", username,
                    "token", token));
            if (!pushed.success()) {
                return StageChangeReports.publishFailed(branch, "Push failed: " + pushed.message());
            }

            List<String> changedPaths = changes.stream().map(FileChange::path).toList();
            String base = "Implementation pushed to branch " + branch
                    + " (" + changes.size() + " files)"
                    + (planFromRepo ? " based on existing implementation plan" : " including new implementation plan");

            if (iteration.retainedPullRequestUrl() != null) {
                // BR-22/R3: skip git_create_pull_request entirely rather than calling and
                // swallowing its guaranteed GitHub 422 for an existing head branch. This makes
                // "a failed repeat PR creation must not be surfaced as an error" structurally
                // true, and saves a network round trip.
                log.info("Pull request {} already exists, git_create_pull_request skipped for run {}",
                        iteration.retainedPullRequestUrl(), runId);
                return StageChangeReports.published(branch, iteration.retainedPullRequestUrl(), changeSet.summary(),
                        changedPaths, base + " — pull request: " + iteration.retainedPullRequestUrl());
            }

            GitToolOutcome pullRequest = git.call(workflowId, runId, customerId, "git_create_pull_request", Map.of(
                    "repositoryUrl", repositoryUrl,
                    "baseBranch", baseBranch,
                    "headBranch", branch,
                    "title", "Implement " + specLabel,
                    "body", SpecGitPublisher.pullRequestBody(
                            "Implementation by run " + runId + " (" + changes.size() + " files changed)",
                            changeSet.summary()),
                    "token", token));
            if (pullRequest.success() && pullRequest.url() != null) {
                log.info("Realisation published to branch {} with pull request {} for run {}",
                        branch, pullRequest.url(), runId);
                return StageChangeReports.published(branch, pullRequest.url(), changeSet.summary(), changedPaths,
                        base + " — pull request: " + pullRequest.url());
            }
            String compare = SpecGitPublisher.compareUrl(repositoryUrl, branch);
            String manual = compare != null ? "; open manually: " + compare : "";
            return StageChangeReports.published(branch, null, changeSet.summary(), changedPaths,
                    base + " (failed to create pull request: " + pullRequest.message() + manual + ")");
        } catch (Exception e) {
            log.warn("Realisation failed for run {}: {}", runId, e.getMessage());
            return StageChangeReports.publishFailed(branch, "Realisation failed: " + e.getMessage());
        }
    }

    /**
     * Searches specs/ for an existing implementation plan; if a feature spec
     * is selected, the plan must reference it. Newest candidates first.
     */
    String findExistingImplementationSpec(String workflowId, String runId, String customerId,
                                          String repositoryUrl, List<String> repositoryFiles,
                                          String featureSpecRef) {
        var candidates = repositoryFiles.stream()
                .filter(f -> f.startsWith(properties.getSpecDirectory() + "/implementation-") && f.endsWith(".md"))
                .sorted(Comparator.reverseOrder())
                .limit(MAX_EXISTING_PLAN_CANDIDATES)
                .toList();
        for (String candidate : candidates) {
            GitToolOutcome read = git.call(workflowId, runId, customerId, "git_read_file", Map.of(
                    "repositoryUrl", repositoryUrl, "path", candidate));
            boolean readable = read.success() && read.content() != null;
            boolean matchesFeatureSpec = readable && (featureSpecRef == null || featureSpecRef.isBlank()
                    || read.content().contains(shortLabel(featureSpecRef)));
            if (matchesFeatureSpec) {
                log.info("Existing implementation plan found: {}", candidate);
                return read.content();
            }
        }
        return null;
    }

    /** Reads the selected feature spec from the repository; otherwise the reference counts as context. */
    String readFeatureSpec(String workflowId, String runId, String customerId, String repositoryUrl,
                           List<String> repositoryFiles, String featureSpecRef) {
        if (featureSpecRef == null || featureSpecRef.isBlank()) {
            return null;
        }
        for (String candidate : List.of(featureSpecRef, properties.getSpecDirectory() + "/" + featureSpecRef)) {
            if (repositoryFiles.contains(candidate)) {
                GitToolOutcome read = git.call(workflowId, runId, customerId, "git_read_file", Map.of(
                        "repositoryUrl", repositoryUrl, "path", candidate));
                if (read.success() && read.content() != null) {
                    return read.content();
                }
            }
        }
        return featureSpecRef;
    }

    private Map<String, String> readContextFiles(String workflowId, String runId, String customerId,
                                                 String repositoryUrl, List<String> repositoryFiles,
                                                 FileSelection selection) {
        Map<String, String> contextFiles = new LinkedHashMap<>();
        List<String> requested = selection != null && selection.paths() != null
                ? selection.paths() : List.of();
        for (String path : requested.stream().distinct().filter(repositoryFiles::contains)
                .limit(MAX_CONTEXT_FILES).toList()) {
            GitToolOutcome read = git.call(workflowId, runId, customerId, "git_read_file", Map.of(
                    "repositoryUrl", repositoryUrl, "path", path));
            if (read.success() && read.content() != null) {
                contextFiles.put(path, read.content());
            }
        }
        return contextFiles;
    }

    List<FileChange> validChanges(CodeChangeSet changeSet) {
        if (changeSet == null || changeSet.changes() == null) {
            return List.of();
        }
        return changeSet.changes().stream().filter(c -> isSafePath(c.path())).toList();
    }

    static boolean isSafePath(String path) {
        return path != null && !path.isBlank()
                && !path.startsWith("/") && !path.contains("..")
                && !path.equals(".git") && !path.startsWith(".git/");
    }

    /** Only the file name if the reference is a path or long text. */
    static String shortLabel(String featureSpecRef) {
        String trimmed = featureSpecRef.trim();
        int slash = trimmed.lastIndexOf('/');
        String name = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
        return name.length() > 60 ? name.substring(0, 60) : name;
    }
}
