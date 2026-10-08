package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalGateContext;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.orchestration.PipelineState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link PipelineState}/{@link ApprovalGateState} → {@link ApprovalGateContext} (architecture
 * §6.4). Consumes ONLY paths from the last {@code StageChangeReport} — never
 * {@code FileChange.content()} — so the approval context cannot become a file-exfiltration
 * channel (AC-13, NFR).
 */
@Component
public class ApprovalGateContextAssembler {

    private final ApprovalGateProperties properties;

    public ApprovalGateContextAssembler(ApprovalGateProperties properties) {
        this.properties = properties;
    }

    public ApprovalGateContext assemble(PipelineState state) {
        ApprovalGateState gate = state.gate();
        var report = gate.lastReport();
        List<String> allPaths = report.changedPaths() != null ? report.changedPaths() : List.of();
        int limit = properties.getMaxChangedPathsInContext();
        List<String> truncated = allPaths.size() > limit ? List.copyOf(allPaths.subList(0, limit)) : allPaths;
        int omittedFileCount = Math.max(0, allPaths.size() - truncated.size());

        String workflowId = workflowId(state.request().requestedBy());
        String repositoryUrl = state.request().repositoryUrl();
        String branchCompareUrl = gate.retainedPullRequestUrl() == null && gate.retainedBranch() != null
                ? compareUrl(repositoryUrl, gate.retainedBranch())
                : null;

        boolean furtherFeedbackAllowed = gate.feedbackSupported() && gate.iteration() <= gate.maxFeedbackIterations();

        return new ApprovalGateContext(
                state.runId(),
                workflowId,
                gate.placementStage(),
                report.outcome(),
                report.reason(),
                report.changeSummary(),
                truncated,
                report.changedFileCount(),
                omittedFileCount,
                gate.retainedBranch(),
                gate.retainedPullRequestUrl(),
                branchCompareUrl,
                gate.iteration(),
                gate.maxFeedbackIterations(),
                gate.feedbackSupported(),
                furtherFeedbackAllowed,
                List.copyOf(gate.commentHistory()),
                gate.openedAt()
        );
    }

    // Deliberately re-implemented rather than widening SpecGitPublisher's package-private
    // helpers of the same name: SpecGitPublisher.java is a forbidden file for this delivery
    // (work plan §4) — its behaviour, git publication points, and visibility must stay untouched.
    private static final String WORKFLOW_PREFIX = "workflow:";

    private static String workflowId(String requestedBy) {
        if (requestedBy != null && requestedBy.startsWith(WORKFLOW_PREFIX)
                && requestedBy.length() > WORKFLOW_PREFIX.length()) {
            return requestedBy.substring(WORKFLOW_PREFIX.length());
        }
        return null;
    }

    private static String compareUrl(String repositoryUrl, String branch) {
        if (repositoryUrl == null || !repositoryUrl.startsWith("http")) {
            return null;
        }
        String base = repositoryUrl.endsWith(".git")
                ? repositoryUrl.substring(0, repositoryUrl.length() - 4) : repositoryUrl;
        return base + "/compare/" + branch + "?expand=1";
    }
}
