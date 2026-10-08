package nl.metafactory.agents.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-40 (no secret leakage): a {@code PUBLISH_FAILED}/{@code NO_CHANGE}/{@code NOT_APPLICABLE}
 * reason must never carry an access token, a git host's userinfo-in-URL credentials, or an
 * {@code Authorization} header value — even though the underlying git tool's raw message (which
 * {@code reason} is derived from) may itself contain one.
 */
class StageChangeReportsSecurityTest {

    @Test
    void publishFailedScrubsAGitHubPersonalAccessTokenFromTheReason() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Push failed: authentication failed for token ghp_abcdefghijklmnopqrstuvwxyz012345");

        assertThat(report.reason()).doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz012345");
        assertThat(report.reason()).contains("***");
    }

    @Test
    void publishFailedScrubsAUserinfoCredentialFromARepositoryUrl() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Failed to create branch: remote rejected https://bot:s3cr3t-pass@github.com/org/repo.git");

        assertThat(report.reason()).doesNotContain("bot:s3cr3t-pass");
        assertThat(report.reason()).contains("https://***@github.com/org/repo.git");
    }

    @Test
    void publishFailedScrubsATokenAssignmentAndAnAuthorizationHeaderValue() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Commit failed: token=ghp_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz1234, "
                        + "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig");

        assertThat(report.reason())
                .doesNotContain("ghp_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz1234")
                .doesNotContain("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig")
                .contains("token=***")
                .contains("Authorization: ***");
    }

    @Test
    void publishFailedScrubsAGitHubFineGrainedPersonalAccessTokenFromTheReason() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run",
                "Push failed: authentication failed for token "
                        + "github_pat_11ABCDE0A0aBcDeFgHiJkL_0123456789abcdefghijklmnopqrstuvwxyz");

        assertThat(report.reason())
                .doesNotContain("github_pat_11ABCDE0A0aBcDeFgHiJkL_0123456789abcdefghijklmnopqrstuvwxyz");
        assertThat(report.reason()).contains("***");
    }

    @Test
    void noChangeAlsoScrubsTheReason() {
        var report = StageChangeReports.noChange("feat/wf-1-run",
                "Commit failed: token=ghp_abcdefghijklmnopqrstuvwxyz012345 rejected");

        assertThat(report.reason()).doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz012345");
    }

    @Test
    void notApplicableAlsoScrubsTheReason() {
        var report = StageChangeReports.notApplicable(
                "Repository inaccessible for https://bot:s3cr3t@github.com/org/repo.git");

        assertThat(report.reason()).doesNotContain("bot:s3cr3t");
    }

    @Test
    void messagesWithoutAnySecretPatternAreLeftUntouched() {
        var report = StageChangeReports.publishFailed("feat/wf-1-run", "Push failed: no write permissions");

        assertThat(report.reason()).isEqualTo("Push failed: no write permissions");
    }

    @Test
    void scrubSecretsReturnsNullForANullMessage() {
        assertThat(StageChangeReports.scrubSecrets(null)).isNull();
    }
}
