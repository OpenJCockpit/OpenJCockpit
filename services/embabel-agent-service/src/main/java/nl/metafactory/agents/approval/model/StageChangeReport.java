package nl.metafactory.agents.approval.model;


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
 * @param changedPaths   changed file paths — paths only, never file contents
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

}
