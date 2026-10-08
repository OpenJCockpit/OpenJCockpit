package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.StageOutcome;
import nl.metafactory.agents.spec.SpecPublication;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every factory, and every {@code toPublication()} mapping asserted against today's exact
 * {@link SpecPublication} (ADR-003) — this is what keeps AC-04 true: an ungated run must see
 * byte-identical {@code SpecPublication} values regardless of the richer {@code StageChangeReport}
 * a gated run's coordinator will read instead.
 */
class StageChangeReportsTest {

    @Test
    void publishedWithPullRequestUrlMapsToTodaysThreeArgPublished() {
        var report = StageChangeReports.published("feat/wf-1-run", "https://github.com/org/repo/pull/9",
                "Validation added", List.of("src/App.java"), "Implementation pushed");

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(report.changeSummary()).isEqualTo("Validation added");
        assertThat(report.changedPaths()).containsExactly("src/App.java");
        assertThat(report.changedFileCount()).isEqualTo(1);
        assertThat(report.reason()).isNull();

        assertThat(report.toPublication()).isEqualTo(
                SpecPublication.published("feat/wf-1-run", "https://github.com/org/repo/pull/9", "Implementation pushed"));
    }

    @Test
    void publishedWithoutPullRequestUrlMapsToTodaysTwoArgPublished() {
        var report = StageChangeReports.published("feat/wf-1-run", null, "summary", List.of(), "message");

        assertThat(report.pullRequestUrl()).isNull();
        assertThat(report.changedPaths()).isEmpty();
        assertThat(report.changedFileCount()).isZero();
        assertThat(report.toPublication()).isEqualTo(SpecPublication.published("feat/wf-1-run", "message"));
    }

    @Test
    void publishedTreatsNullChangedPathsAsEmpty() {
        var report = StageChangeReports.published("feat/wf-1-run", null, null, null, "message");

        assertThat(report.changedPaths()).isEmpty();
        assertThat(report.changedFileCount()).isZero();
    }

    @Test
    void publishFailedMapsToTodaysFailedAndScrubsTheReason() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Push failed: https://bot:ghp_abcdefghijklmnopqrstuvwxyz012345@github.com/org/repo.git");

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(report.branch()).isEqualTo("feat/wf-1-run");
        assertThat(report.reason()).doesNotContain("bot:ghp_");
        // message (used by AgentEvent/toPublication for an ungated run) is intentionally left
        // unscrubbed here — it is what recordPublication has always emitted, and none of today's
        // real failure messages contain a credential (they are fixed Dutch phrases plus a git
        // tool's own message, which is scrubbed separately into `reason`).
        assertThat(report.toPublication()).isEqualTo(SpecPublication.failed("feat/wf-1-run", report.message()));
    }

    @Test
    void noChangeAlsoMapsToTodaysFailedViaToPublication() {
        var report = StageChangeReports.noChange("feat/wf-1-run", "Realisation agent produced no changes");

        assertThat(report.outcome()).isEqualTo(StageOutcome.NO_CHANGE);
        assertThat(report.toPublication()).isEqualTo(
                SpecPublication.failed("feat/wf-1-run", "Realisation agent produced no changes"));
    }

    @Test
    void notApplicableMapsToTodaysSkipped() {
        var report = StageChangeReports.notApplicable("No repository URL configured");

        assertThat(report.outcome()).isEqualTo(StageOutcome.NOT_APPLICABLE);
        assertThat(report.changeSummary()).isEqualTo("No repository URL configured");
        assertThat(report.toPublication()).isEqualTo(
                SpecPublication.skipped("No repository URL configured"));
    }

    @Test
    void fromPublicationMapsOkToPublished() {
        var publication = SpecPublication.published("spec/wf-1-run", "Spec pushed");

        var report = StageChangeReports.fromPublication(publication, List.of("specs/spec-1.md"));

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISHED);
        assertThat(report.branch()).isEqualTo("spec/wf-1-run");
        assertThat(report.changedPaths()).containsExactly("specs/spec-1.md");
        assertThat(report.toPublication()).isEqualTo(publication);
    }

    @Test
    void fromPublicationMapsSkippedToNotApplicable() {
        var publication = SpecPublication.skipped("Spec git publication is disabled");

        var report = StageChangeReports.fromPublication(publication, List.of());

        assertThat(report.outcome()).isEqualTo(StageOutcome.NOT_APPLICABLE);
        assertThat(report.toPublication()).isEqualTo(publication);
    }

    @Test
    void fromPublicationMapsFailedToPublishFailed() {
        var publication = SpecPublication.failed("spec/wf-1-run", "Push failed: no write permissions");

        var report = StageChangeReports.fromPublication(publication, List.of());

        assertThat(report.outcome()).isEqualTo(StageOutcome.PUBLISH_FAILED);
        assertThat(report.toPublication()).isEqualTo(publication);
    }
}
