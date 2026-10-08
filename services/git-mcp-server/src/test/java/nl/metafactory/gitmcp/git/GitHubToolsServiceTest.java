package nl.metafactory.gitmcp.git;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubToolsServiceTest {

    private static final String REPO_URL = "https://github.com/org/repo.git";

    private final List<String> calls = new ArrayList<>();
    private final GitHubOperations recordingGitHub =
            (apiBase, owner, repo, head, base, title, body, token) -> {
                calls.add(String.join("|", apiBase, owner, repo, head, base, title, body, token));
                return "https://github.com/org/repo/pull/7";
            };

    @Test
    void createPullRequestReturnsThePullRequestUrl() {
        var service = new GitHubToolsService(recordingGitHub);

        var result = service.createPullRequest(REPO_URL, "main", "impl/001-feature",
                "Implement feature", "Description", "secret");

        assertThat(result.success()).isTrue();
        assertThat(result.url()).isEqualTo("https://github.com/org/repo/pull/7");
        assertThat(result.message()).contains("impl/001-feature").contains("main");
        assertThat(calls).containsExactly(
                "https://api.github.com|org|repo|impl/001-feature|main|Implement feature|Description|secret");
    }

    @Test
    void createPullRequestTreatsNullBodyAsEmpty() {
        var service = new GitHubToolsService(recordingGitHub);

        var result = service.createPullRequest(REPO_URL, "main", "impl/x", "Title", null, "secret");

        assertThat(result.success()).isTrue();
        assertThat(calls.get(0)).contains("|Title||secret");
    }

    @Test
    void createPullRequestFailsWithoutToken() {
        var service = new GitHubToolsService(recordingGitHub);

        var nullToken = service.createPullRequest(REPO_URL, "main", "impl/x", "Title", "", null);
        var blankToken = service.createPullRequest(REPO_URL, "main", "impl/x", "Title", "", " ");

        assertThat(nullToken.success()).isFalse();
        assertThat(nullToken.message()).contains("token is required");
        assertThat(blankToken.success()).isFalse();
        assertThat(calls).isEmpty();
    }

    @Test
    void createPullRequestReturnsFailureOnApiError() {
        GitHubOperations failing = (apiBase, owner, repo, head, base, title, body, token) -> {
            throw new IllegalStateException("GitHub API returned 422: validation failed");
        };
        var service = new GitHubToolsService(failing);

        var result = service.createPullRequest(REPO_URL, "main", "impl/x", "Title", "", "secret");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_create_pull_request failed")
                .contains("422");
    }

    @Test
    void apiBaseUsesGitHubComApiOrEnterprisePath() {
        assertThat(GitHubToolsService.apiBaseOf("https://github.com/org/repo.git"))
                .isEqualTo("https://api.github.com");
        assertThat(GitHubToolsService.apiBaseOf("https://git.example.com/org/repo.git"))
                .isEqualTo("https://git.example.com/api/v3");
        assertThatThrownBy(() -> GitHubToolsService.apiBaseOf("file:///tmp/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no host");
    }

    @Test
    void ownerAndRepositoryAreParsedFromTheUrl() {
        assertThat(GitHubToolsService.ownerOf(REPO_URL)).isEqualTo("org");
        assertThat(GitHubToolsService.repositoryOf(REPO_URL)).isEqualTo("repo");
        assertThat(GitHubToolsService.repositoryOf("https://github.com/org/repo")).isEqualTo("repo");
        assertThatThrownBy(() -> GitHubToolsService.ownerOf("https://github.com/owner-only"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner and repository");
    }

    @Test
    void gitHubToolResultFactories() {
        var ok = GitHubToolResult.ok("done", "https://github.com/org/repo/pull/1");
        assertThat(ok.success()).isTrue();
        assertThat(ok.message()).isEqualTo("done");
        assertThat(ok.url()).isEqualTo("https://github.com/org/repo/pull/1");

        var failure = GitHubToolResult.failure("boom");
        assertThat(failure.success()).isFalse();
        assertThat(failure.url()).isNull();
    }
}
