package nl.metafactory.gitmcp.git;

/** Result of a GitHub tool; url contains the created pull request. */
public record GitHubToolResult(
        boolean success,
        String message,
        String url
) {
    public static GitHubToolResult ok(String message, String url) {
        return new GitHubToolResult(true, message, url);
    }

    public static GitHubToolResult failure(String message) {
        return new GitHubToolResult(false, message, null);
    }
}
