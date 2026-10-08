package nl.metafactory.agents.approval.model;

import nl.metafactory.agents.spec.SpecPublication;

import java.util.List;

/**
 * The single gate-seam type (ADR-003): every gated stage's block produces exactly one of these,
 * built exclusively by {@link nl.metafactory.agents.approval.StageChangeReports}, and every gate
 * opens through exactly one method, {@code ApprovalGateCoordinator.pauseAfter}, fed by this type.
 *
 * @param outcome        one of the four BR-40 outcomes
 * @param branch         branch name, if any git publication was attempted
 * @param pullRequestUrl pull-request URL, only for a clean {@link StageOutcome#PUBLISHED} outcome
 * @param changeSummary  agent-authored prose summary of the change, if any
 * @param changedPaths   changed file paths — paths only, never {@code FileChange.content()}
 * @param changedFileCount the number of changed files (may exceed a later-truncated context list)
 * @param reason         plain-language, credential-scrubbed explanation for a non-published outcome
 * @param message        the human string {@code recordPublication}/{@code AgentEvent} use today
 */
public record StageChangeReport(
        StageOutcome outcome,
        String branch,
        String pullRequestUrl,
        String changeSummary,
        List<String> changedPaths,
        int changedFileCount,
        String reason,
        String message
) {

    /**
     * Reconstructs exactly today's {@link SpecPublication} for each outcome, so that
     * {@code recordPublication} emits byte-identical {@code AgentEvent}s and
     * {@code generatedArtifacts} for an ungated run — this is what keeps AC-04 true after
     * {@link nl.metafactory.agents.spec.CodeRealisationService} switched its return type.
     */
    public SpecPublication toPublication() {
        return switch (outcome) {
            case PUBLISHED -> pullRequestUrl != null
                    ? SpecPublication.published(branch, pullRequestUrl, message)
                    : SpecPublication.published(branch, message);
            case PUBLISH_FAILED, NO_CHANGE -> SpecPublication.failed(branch, message);
            case NOT_APPLICABLE -> SpecPublication.skipped(message);
        };
    }
}
