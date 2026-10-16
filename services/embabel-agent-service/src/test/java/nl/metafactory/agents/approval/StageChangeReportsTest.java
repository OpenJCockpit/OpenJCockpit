package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.StageOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every {@link StageChangeReports} factory produces the outcome the approval gate reads. */
class StageChangeReportsTest {

    @Test
    void publishedCarriesSummaryAndChangedPaths() {
        var report = StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "Validation added", List.of("src/App.java"), "Implementation pushed");

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(report.changeSummary()).isEqualTo("Validation added");
        assertThat(report.changedPaths()).containsExactly("src/App.java");
        assertThat(report.changedFileCount()).isEqualTo(1);
        assertThat(report.reason()).isNull();
    }

    @Test
    void publishedWithoutPullRequestUrlHasNoUrl() {
        var report = StageChangeReports.published("feat/wf-1-run", null, "summary", List.of(), "message");

        assertThat(report.pullRequestUrl()).isNull();
        assertThat(report.changedPaths()).isEmpty();
        assertThat(report.changedFileCount()).isZero();
    }

    @Test
    void publishedTreatsNullChangedPathsAsEmpty() {
        var report = StageChangeReports.published("feat/wf-1-run", null, null, null, "message");

        assertThat(report.changedPaths()).isEmpty();
        assertThat(report.changedFileCount()).isZero();
    }

    @Test
    void publishFailedScrubsTheReason() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Push failed: https://bot:ghp_abcdefghijklmnopqrstuvwxyz012345@github.com/org/repo.git");

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(report.branch()).isEqualTo("feat/wf-1-run");
        assertThat(report.reason()).doesNotContain("bot:ghp_");
    }

    @Test
    void noChangeHasTheNoChangeOutcome() {
        var report = StageChangeReports.noChange("feat/wf-1-run", "Agent produced no changes");

        assertThat(report.outcome()).isEqualTo(StageOutcome.NO_CHANGE);
    }

    @Test
    void notApplicableCarriesTheMessageAsSummary() {
        var report = StageChangeReports.notApplicable("Agent local:x produced 12 characters of output");

        assertThat(report.outcome()).isEqualTo(StageOutcome.NOT_APPLICABLE);
        assertThat(report.changeSummary()).isEqualTo("Agent local:x produced 12 characters of output");
    }
}
