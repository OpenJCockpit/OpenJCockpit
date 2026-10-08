package nl.metafactory.agents.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the exact literal ADR-006 depends on — see
 * {@code services/git-mcp-server/src/main/java/nl/metafactory/gitmcp/git/GitToolsService.java:108}:
 * {@code GitToolResult.failure("Nothing to commit: workspace has no uncommitted changes")}.
 */
class GitCommitOutcomeClassifierTest {

    @Test
    void isNothingToCommitMatchesTheExactGitMcpServerLiteral() {
        assertThat(GitCommitOutcomeClassifier.isNothingToCommit(
                "Nothing to commit: workspace has no uncommitted changes")).isTrue();
    }

    @Test
    void isNothingToCommitRejectsAnyOtherMessage() {
        assertThat(GitCommitOutcomeClassifier.isNothingToCommit("nothing to commit, working tree clean")).isFalse();
        assertThat(GitCommitOutcomeClassifier.isNothingToCommit("Nothing to commit")).isFalse();
        assertThat(GitCommitOutcomeClassifier.isNothingToCommit(null)).isFalse();
    }
}
