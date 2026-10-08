package nl.metafactory.gitmcp.git;

/** Result of git_read_file: the content of a single file from the workspace. */
public record GitFileResult(
        boolean success,
        String message,
        String path,
        String content
) {
    public static GitFileResult ok(String message, String path, String content) {
        return new GitFileResult(true, message, path, content);
    }

    public static GitFileResult failure(String message) {
        return new GitFileResult(false, message, null, null);
    }
}
