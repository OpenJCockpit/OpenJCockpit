package nl.metafactory.aicontrol.service;

public interface GitHubPort {

    void checkRepository(String owner, String repo, String apiUrl, String token) throws Exception;

    GitHubPullRequestState readPullRequest(GitHubPullRequestRef ref, String apiUrl, String token)
            throws GitHubApiException;

    /** Sends at most one squash-merge request, guarded by the expected head SHA. */
    void squashMergePullRequest(GitHubPullRequestRef ref, String expectedHeadSha, String commitMessage,
                                String apiUrl, String token) throws GitHubApiException;
}
