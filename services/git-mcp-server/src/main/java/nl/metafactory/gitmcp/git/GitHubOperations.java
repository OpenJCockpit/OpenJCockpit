package nl.metafactory.gitmcp.git;

/**
 * Port to the GitHub REST API. The MCP tools talk exclusively through this
 * interface so that the tool logic can be tested without real GitHub calls.
 */
@FunctionalInterface
public interface GitHubOperations {

    /** Creates a pull request and returns the HTML URL of the PR. */
    String createPullRequest(String apiBase, String owner, String repo,
                             String headBranch, String baseBranch,
                             String title, String body, String token) throws Exception;
}
