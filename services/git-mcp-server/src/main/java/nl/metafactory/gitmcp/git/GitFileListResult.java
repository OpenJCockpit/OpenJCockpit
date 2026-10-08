package nl.metafactory.gitmcp.git;

import java.util.List;

/** Result of git_list_files: file paths relative to the repository root. */
public record GitFileListResult(
        boolean success,
        String message,
        List<String> files
) {
    public static GitFileListResult ok(String message, List<String> files) {
        return new GitFileListResult(true, message, files);
    }

    public static GitFileListResult failure(String message) {
        return new GitFileListResult(false, message, null);
    }
}
