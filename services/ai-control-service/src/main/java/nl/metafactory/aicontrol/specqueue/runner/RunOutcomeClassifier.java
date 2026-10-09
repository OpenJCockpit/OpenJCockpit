package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.AgentEventDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.specqueue.github.PullRequestUrlParser;
import nl.metafactory.aicontrol.specqueue.planner.RunOutcome;
import nl.metafactory.aicontrol.specqueue.planner.RunPullRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure: maps a run read from the agent service to an outcome. Only fixed codes and validated coordinates leave. */
@Component
public class RunOutcomeClassifier {

    private static final Set<String> STATUSES = Set.of("RUNNING", "AWAITING_CHILD_WORKFLOW", "AWAITING_APPROVAL",
            "COMPLETED", "FAILED", "DENIED", "BLOCKED", "TIMED_OUT", "RUN_STATE_LOST", "CANCELLED");
    private static final String PR_PREFIX = "pull-request:";
    private static final String BRANCH_PREFIX = "git-branch:";

    public record RunFeatures(Long durationMs, int reviewIterations, int approvalGateIterations,
                              String approvalGateOutcome) {}

    public String normalizeStatus(String status) {
        if (status == null) return "OTHER";
        String s = status.strip().toUpperCase(Locale.ROOT);
        return STATUSES.contains(s) ? s : "OTHER";
    }

    public RunOutcome classify(AgentRunDto run, boolean cancelRequested, String projectGitUrl) {
        String status = normalizeStatus(run.status());
        return switch (status) {
            case "FAILED" -> new RunOutcome.Failed(SpecQueueFailureReason.RUN_FAILED);
            case "DENIED" -> new RunOutcome.Failed(SpecQueueFailureReason.APPROVAL_DENIED);
            case "BLOCKED" -> new RunOutcome.Failed(SpecQueueFailureReason.RUN_BLOCKED);
            case "TIMED_OUT" -> new RunOutcome.Failed(SpecQueueFailureReason.RUN_TIMED_OUT);
            case "RUN_STATE_LOST" -> new RunOutcome.Failed(SpecQueueFailureReason.RUN_STATE_LOST);
            case "CANCELLED" -> cancelRequested ? new RunOutcome.CancelledByQueue()
                    : new RunOutcome.Failed(SpecQueueFailureReason.RUN_CANCELLED);
            case "COMPLETED" -> classifyCompleted(run, projectGitUrl);
            default -> new RunOutcome.NonTerminal(status);
        };
    }

    private RunOutcome classifyCompleted(AgentRunDto run, String projectGitUrl) {
        for (AgentEventDto e : nonNull(run.events())) {
            if (e.status() != null && e.status().strip().equalsIgnoreCase("FAILED")) {
                return new RunOutcome.Failed(SpecQueueFailureReason.PUBLICATION_FAILED);
            }
        }
        Map<String, RunPullRequest> prs = new LinkedHashMap<>();
        boolean branch = false;
        for (String artifact : nonNull(run.generatedArtifacts())) {
            if (artifact.startsWith(PR_PREFIX)) {
                var result = PullRequestUrlParser.parse(artifact.substring(PR_PREFIX.length()).strip(), projectGitUrl);
                if (result instanceof PullRequestUrlParser.Invalid invalid) {
                    return new RunOutcome.Failed(invalid.reason());
                }
                var valid = (PullRequestUrlParser.Valid) result;
                prs.putIfAbsent(valid.canonicalUrl().toLowerCase(Locale.ROOT), new RunPullRequest(
                        valid.canonicalUrl(), valid.ref().owner(), valid.ref().repo(), valid.ref().number()));
            } else if (artifact.startsWith(BRANCH_PREFIX)) {
                branch = true;
            }
        }
        if (prs.isEmpty()) {
            return branch ? new RunOutcome.Failed(SpecQueueFailureReason.PR_NOT_CREATED)
                    : new RunOutcome.SucceededNoChanges();
        }
        List<RunPullRequest> list = new ArrayList<>(prs.values());
        return list.size() == 1 ? new RunOutcome.SucceededWithPr(list.get(0)) : new RunOutcome.SucceededWithPrs(list);
    }

    public RunFeatures features(AgentRunDto run) {
        Long duration = run.startedAt() == null || run.completedAt() == null ? null
                : Math.max(0, Duration.between(run.startedAt(), run.completedAt()).toMillis());
        int reviews = 0;
        int gates = 0;
        for (AgentEventDto e : nonNull(run.events())) {
            String s = e.status();
            if ("review".equals(e.agentId()) && ("OK".equals(s) || "WAITING".equals(s))) reviews++;
            if ("AWAITING_APPROVAL".equals(s)) gates++;
        }
        String outcome = gates == 0 ? "NONE" : "DENIED".equals(normalizeStatus(run.status())) ? "DENIED" : "ACCEPTED";
        return new RunFeatures(duration, reviews, gates, outcome);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list.stream().filter(java.util.Objects::nonNull).toList();
    }
}
