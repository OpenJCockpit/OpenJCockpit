package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.StageChangeReport;
import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.spec.SpecPublication;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The ONLY producer of {@link StageChangeReport} (ADR-003). {@link StageOutcome} must never
 * appear in a control-flow condition outside this class and presentation — no
 * {@code openGateForFailure}/{@code openGateForNoChange} helper, no {@code boolean
 * publicationFailed} flag; those are exactly risk 3 / R4.
 */
public final class StageChangeReports {

    // AC-40: never let a credential leak into the approval context's outcomeReason, including a
    // git host's userinfo-in-URL form or a raw token/Authorization value echoed from a tool result.
    private static final Pattern USERINFO_URL = Pattern.compile("https://[^\\s@/]+:[^\\s@/]+@");
    private static final Pattern TOKEN_ASSIGNMENT = Pattern.compile("(?i)\\btoken\\s*[:=]\\s*\\S+");
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile("(?i)\\bAuthorization\\s*:\\s*\\S+(\\s+\\S+)?");
    private static final Pattern GITHUB_PAT =
            Pattern.compile("\\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b");

    private StageChangeReports() {
    }

    /** Stage completed and published cleanly (BR-40 PUBLISHED). */
    public static StageChangeReport published(String branch, String pullRequestUrl, String changeSummary,
                                               List<String> changedPaths, String message) {
        List<String> paths = changedPaths != null ? changedPaths : List.of();
        return new StageChangeReport(StageOutcome.PUBLISHED, branch, pullRequestUrl, changeSummary, paths,
                paths.size(), null, message);
    }

    /** Stage produced changes but branch/commit/push/PR failed (BR-40 PUBLISH_FAILED). */
    public static StageChangeReport publishFailed(String branch, String message) {
        return new StageChangeReport(StageOutcome.PUBLISH_FAILED, branch, null, null, List.of(), 0,
                scrubSecrets(message), message);
    }

    /** Agent returned no implementable change, or nothing new to commit (BR-40 NO_CHANGE). */
    public static StageChangeReport noChange(String branch, String message) {
        return new StageChangeReport(StageOutcome.NO_CHANGE, branch, null, null, List.of(), 0,
                scrubSecrets(message), message);
    }

    /** The gated stage performs no git publication at all (BR-40 NOT_APPLICABLE). */
    public static StageChangeReport notApplicable(String message) {
        return new StageChangeReport(StageOutcome.NOT_APPLICABLE, null, null, message, List.of(), 0,
                scrubSecrets(message), message);
    }

    /**
     * Bridges a non-realisation git publication (today's {@code publishSpecToGit}/
     * {@code publishImplementationToGit}, whose {@link SpecPublication} carries only
     * OK/SKIPPED/FAILED) into a {@link StageChangeReport}, for stages other than realisation.
     */
    public static StageChangeReport fromPublication(SpecPublication publication, List<String> changedPaths) {
        return switch (publication.status()) {
            case "OK" -> published(publication.branch(), publication.pullRequestUrl(), null,
                    changedPaths, publication.message());
            case "SKIPPED" -> notApplicable(publication.message());
            default -> publishFailed(publication.branch(), publication.message());
        };
    }

    /**
     * Fixed-vocabulary-plus-scrub scrubber for {@code outcomeReason} (AC-40): removes userinfo
     * credentials from a URL, a {@code token=}/{@code Authorization:} value, and a bare GitHub
     * personal access token, wherever they appear in a git tool's raw message.
     */
    static String scrubSecrets(String message) {
        if (message == null) {
            return null;
        }
        String scrubbed = USERINFO_URL.matcher(message).replaceAll("https://***@");
        scrubbed = AUTHORIZATION_HEADER.matcher(scrubbed).replaceAll("Authorization: ***");
        scrubbed = TOKEN_ASSIGNMENT.matcher(scrubbed).replaceAll("token=***");
        scrubbed = GITHUB_PAT.matcher(scrubbed).replaceAll("***");
        return scrubbed;
    }
}
