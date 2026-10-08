package nl.metafactory.gitmcp.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * GitHub integration as an MCP tool, alongside the git workspace tools in
 * {@link GitToolsService}: after a pushed branch, an agent can use this to
 * actually open a pull request instead of only sharing a compare URL.
 */
@Service
public class GitHubToolsService {

    private static final Logger log = LoggerFactory.getLogger(GitHubToolsService.class);

    private final GitHubOperations gitHub;

    public GitHubToolsService(GitHubOperations gitHub) {
        this.gitHub = gitHub;
    }

    @Tool(name = "git_create_pull_request", description = "Create a pull request on GitHub for a branch that "
            + "was pushed to the project repository. Returns the URL of the created pull request.")
    public GitHubToolResult createPullRequest(
            @ToolParam(description = "HTTPS URL of the project git repository, e.g. https://github.com/org/repo.git") String repositoryUrl,
            @ToolParam(description = "Base branch the pull request should merge into, e.g. main") String baseBranch,
            @ToolParam(description = "Branch with the changes, e.g. impl/001-feature") String headBranch,
            @ToolParam(description = "Title of the pull request") String title,
            @ToolParam(description = "Description (markdown body) of the pull request", required = false) String body,
            @ToolParam(description = "GitHub token with permission to create pull requests") String token) {
        try {
            if (token == null || token.isBlank()) {
                return GitHubToolResult.failure("A GitHub token is required to create a pull request");
            }
            String owner = ownerOf(repositoryUrl);
            String repo = repositoryOf(repositoryUrl);
            String url = gitHub.createPullRequest(apiBaseOf(repositoryUrl), owner, repo,
                    headBranch, baseBranch, title, body != null ? body : "", token);
            return GitHubToolResult.ok("Created pull request for " + headBranch + " into " + baseBranch, url);
        } catch (Exception e) {
            log.warn("git_create_pull_request failed: {}", e.getMessage());
            return GitHubToolResult.failure("git_create_pull_request failed: " + e.getMessage());
        }
    }

    // ── URL helpers ──────────────────────────────────────────────────────────

    static String apiBaseOf(String repositoryUrl) {
        String host = URI.create(repositoryUrl).getHost();
        if (host == null) {
            throw new IllegalArgumentException("Repository URL has no host: " + repositoryUrl);
        }
        return "github.com".equals(host) ? "https://api.github.com" : "https://" + host + "/api/v3";
    }

    static String ownerOf(String repositoryUrl) {
        return pathSegments(repositoryUrl)[0];
    }

    static String repositoryOf(String repositoryUrl) {
        String repo = pathSegments(repositoryUrl)[1];
        return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
    }

    private static String[] pathSegments(String repositoryUrl) {
        String path = URI.create(repositoryUrl).getPath();
        String[] segments = path == null ? new String[0]
                : java.util.Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (segments.length < 2) {
            throw new IllegalArgumentException("Repository URL must contain owner and repository: " + repositoryUrl);
        }
        return segments;
    }
}
