package nl.metafactory.gitmcp.git;

/** Uniform result of every git tool, so that agents can parse success and detail. */
public record GitToolResult(
        boolean success,
        String message,
        String workspace,
        String branch,
        String commitHash
) {
    public static GitToolResult ok(String message, String workspace, String branch, String commitHash) {
        return new GitToolResult(true, message, workspace, branch, commitHash);
    }

    public static GitToolResult failure(String message) {
        return new GitToolResult(false, message, null, null, null);
    }
}
