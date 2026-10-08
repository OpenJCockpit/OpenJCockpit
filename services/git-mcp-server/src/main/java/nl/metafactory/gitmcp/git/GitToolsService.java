package nl.metafactory.gitmcp.git;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The git integration as MCP tools. Each tool receives the project details for the
 * git integration (repository URL, base branch and credentials) and works in
 * one workspace clone per repository under the configured base path. All
 * agents use these tools via the central MCP client, so that changes to
 * the git implementation happen in one place.
 */
@Service
public class GitToolsService {

    private static final Logger log = LoggerFactory.getLogger(GitToolsService.class);
    static final int MAX_LISTED_FILES = 500;
    static final int MAX_FILE_CHARS = 100_000;

    private static final Pattern WORKSPACE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    static boolean isValidWorkspaceKey(String workspaceKey) {
        return workspaceKey == null || workspaceKey.isBlank() || WORKSPACE_KEY_PATTERN.matcher(workspaceKey).matches();
    }

    private final GitWorkspaceOperations gitOps;
    private final GitToolsProperties properties;

    public GitToolsService(GitWorkspaceOperations gitOps, GitToolsProperties properties) {
        this.gitOps = gitOps;
        this.properties = properties;
    }

    @Tool(name = "git_checkout_branch", description = "Check out an existing branch of the project repository. "
            + "Clones the repository into the server-managed workspace when needed.")
    public GitToolResult checkoutBranch(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Branch to check out") String branch,
            @ToolParam(description = "Git username, empty for anonymous access", required = false) String username,
            @ToolParam(description = "Git token or password, empty for anonymous access", required = false) String token,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from " + "other concurrent runs against the same repository. Omit for the shared workspace.", required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = ensureWorkspace(repositoryUrl, branch, username, token, workspaceKey);
            if (!branch.equals(gitOps.currentBranch(workspace))) {
                gitOps.checkoutBranch(workspace, branch, false);
            }
            return GitToolResult.ok("Checked out branch " + branch, workspace.toString(), branch, null);
        } catch (Exception e) {
            return failure("git_checkout_branch", e);
        }
    }

    GitToolResult createBranch(String repositoryUrl, String baseBranch, String newBranch, String username, String token) {
        return createBranch(repositoryUrl, baseBranch, newBranch, username, token, null);
    }

    @Tool(name = "git_create_branch", description = "Create and check out a new branch from a base branch of the "
            + "project repository, e.g. for a new spec or implementation change.")
    public GitToolResult createBranch(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Base branch to branch off from, e.g. main") String baseBranch,
            @ToolParam(description = "Name of the new branch, e.g. spec/001-feature-slug") String newBranch,
            @ToolParam(description = "Git username, empty for anonymous access", required = false) String username,
            @ToolParam(description = "Git token or password, empty for anonymous access", required = false) String token,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = ensureWorkspace(repositoryUrl, baseBranch, username, token, workspaceKey);
            if (!gitOps.ensureLocalBranch(workspace, baseBranch, credentials(username, token),
                    properties.getTimeoutSeconds())) {
                return GitToolResult.failure(
                        "git_create_branch failed: base branch not found locally or on origin: " + baseBranch);
            }
            if (!baseBranch.equals(gitOps.currentBranch(workspace))) {
                gitOps.checkoutBranch(workspace, baseBranch, false);
            }
            gitOps.pull(workspace, credentials(username, token), properties.getTimeoutSeconds());
            gitOps.checkoutBranch(workspace, newBranch, true);
            return GitToolResult.ok("Created branch " + newBranch + " from " + baseBranch,
                    workspace.toString(), newBranch, null);
        } catch (Exception e) {
            return failure("git_create_branch", e);
        }
    }

    GitToolResult pull(String repositoryUrl, String branch, String username, String token) {
        return pull(repositoryUrl, branch, username, token, null);
    }

    @Tool(name = "git_pull", description = "Pull the latest changes for the currently checked out branch "
            + "of the project repository.")
    public GitToolResult pull(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Branch that should be current before pulling") String branch,
            @ToolParam(description = "Git username, empty for anonymous access", required = false) String username,
            @ToolParam(description = "Git token or password, empty for anonymous access", required = false) String token,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = ensureWorkspace(repositoryUrl, branch, username, token, workspaceKey);
            if (!branch.equals(gitOps.currentBranch(workspace))) {
                gitOps.checkoutBranch(workspace, branch, false);
            }
            gitOps.pull(workspace, credentials(username, token), properties.getTimeoutSeconds());
            return GitToolResult.ok("Pulled latest changes for " + branch, workspace.toString(), branch, null);
        } catch (Exception e) {
            return failure("git_pull", e);
        }
    }

    GitToolResult commit(String repositoryUrl, String message, String authorName, String authorEmail) {
        return commit(repositoryUrl, message, authorName, authorEmail, null);
    }

    @Tool(name = "git_commit", description = "Stage all changes in the repository workspace and commit them "
            + "with the given message. Returns the commit hash.")
    public GitToolResult commit(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Commit message") String message,
            @ToolParam(description = "Author name for the commit", required = false) String authorName,
            @ToolParam(description = "Author email for the commit", required = false) String authorEmail,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            if (!gitOps.hasUncommittedChanges(workspace)) {
                return GitToolResult.failure("Nothing to commit: workspace has no uncommitted changes");
            }
            gitOps.stageAll(workspace);
            String name = authorName != null && !authorName.isBlank() ? authorName : "Agentic Workflow";
            String email = authorEmail != null && !authorEmail.isBlank() ? authorEmail : "agentic@metafactory.nl";
            String hash = gitOps.commitWithMessage(workspace, message, name, email);
            return GitToolResult.ok("Committed changes", workspace.toString(),
                    gitOps.currentBranch(workspace), hash);
        } catch (Exception e) {
            return failure("git_commit", e);
        }
    }

    GitFileListResult listFiles(String repositoryUrl, String subdirectory) {
        return listFiles(repositoryUrl, subdirectory, null);
    }

    @Tool(name = "git_list_files", description = "List the file paths (relative to the repository root) in "
            + "the repository workspace, optionally limited to a subdirectory. Check out or create a branch first.")
    public GitFileListResult listFiles(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Subdirectory to list, relative to the repository root; empty for the whole repository",
                    required = false) String subdirectory,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitFileListResult.failure("invalid workspaceKey");
            }
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            Path root = subdirectory == null || subdirectory.isBlank()
                    ? workspace : insideWorkspace(workspace, subdirectory);
            if (!Files.isDirectory(root)) {
                return GitFileListResult.failure("Not a directory: " + subdirectory);
            }
            try (var paths = Files.walk(root)) {
                List<String> files = paths.filter(Files::isRegularFile)
                        .map(workspace::relativize)
                        .map(Path::toString)
                        .map(p -> p.replace('\\', '/'))
                        .filter(p -> !p.startsWith(".git/"))
                        .sorted()
                        .limit(MAX_LISTED_FILES)
                        .toList();
                return GitFileListResult.ok("Listed " + files.size() + " files", files);
            }
        } catch (Exception e) {
            log.warn("git_list_files failed: {}", e.getMessage());
            return GitFileListResult.failure("git_list_files failed: " + e.getMessage());
        }
    }

    GitFileResult readFile(String repositoryUrl, String path) {
        return readFile(repositoryUrl, path, null);
    }

    @Tool(name = "git_read_file", description = "Read the text content of a file at a path relative to the "
            + "repository root. Check out or create a branch first.")
    public GitFileResult readFile(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "File path relative to the repository root, e.g. src/main/App.java") String path,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitFileResult.failure("invalid workspaceKey");
            }
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            Path target = insideWorkspace(workspace, path);
            if (!Files.isRegularFile(target)) {
                return GitFileResult.failure("File not found: " + path);
            }
            String content = Files.readString(target);
            if (content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS);
            }
            return GitFileResult.ok("Read " + path, path, content);
        } catch (Exception e) {
            log.warn("git_read_file failed: {}", e.getMessage());
            return GitFileResult.failure("git_read_file failed: " + e.getMessage());
        }
    }

    GitToolResult writeFile(String repositoryUrl, String path, String content) {
        return writeFile(repositoryUrl, path, content, null);
    }

    @Tool(name = "git_write_file", description = "Write text content to a file at a path relative to the "
            + "repository root, e.g. specs/spec-001.md. Creates parent directories as needed. Check out or "
            + "create a branch first; commit and push afterwards.")
    public GitToolResult writeFile(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "File path relative to the repository root, e.g. specs/spec-001.md") String path,
            @ToolParam(description = "Full text content of the file") String content,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            Path target = insideWorkspace(workspace, path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content != null ? content : "");
            return GitToolResult.ok("Wrote file " + path, workspace.toString(),
                    gitOps.currentBranch(workspace), null);
        } catch (Exception e) {
            return failure("git_write_file", e);
        }
    }

    GitToolResult push(String repositoryUrl, String branch, String username, String token) {
        return push(repositoryUrl, branch, username, token, null);
    }

    @Tool(name = "git_push", description = "Push a branch of the repository workspace to origin so a "
            + "pull request can be opened.")
    public GitToolResult push(
            @ToolParam(description = "HTTPS or file URL of the project git repository") String repositoryUrl,
            @ToolParam(description = "Branch to push to origin") String branch,
            @ToolParam(description = "Git username, empty for anonymous access", required = false) String username,
            @ToolParam(description = "Git token or password, empty for anonymous access", required = false) String token,
            @ToolParam(description = "Optional opaque key isolating this caller's workspace clone from "
                    + "other concurrent runs against the same repository. Omit for the shared workspace.",
                    required = false) String workspaceKey) {
        try {
            if (!isValidWorkspaceKey(workspaceKey)) {
                return GitToolResult.failure("invalid workspaceKey");
            }
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            gitOps.pushBranch(workspace, "origin", branch, credentials(username, token),
                    properties.getTimeoutSeconds());
            return GitToolResult.ok("Pushed branch " + branch + " to origin", workspace.toString(), branch, null);
        } catch (Exception e) {
            return failure("git_push", e);
        }
    }

    // ── Workspace helpers ────────────────────────────────────────────────────

    /** One workspace clone per repository; if it does not exist yet, the branch is cloned. */
    Path ensureWorkspace(String repositoryUrl, String branch, String username, String token) throws Exception {
        return ensureWorkspace(repositoryUrl, branch, username, token, null);
    }

    Path ensureWorkspace(String repositoryUrl, String branch, String username, String token, String workspaceKey) throws Exception {
        Path workspace = workspacePath(repositoryUrl, workspaceKey);
        if (Files.isDirectory(workspace.resolve(".git"))) {
            return workspace;
        }
        Files.createDirectories(workspace.getParent());
        gitOps.cloneRepository(repositoryUrl, branch, workspace, credentials(username, token),
                properties.getTimeoutSeconds());
        return workspace;
    }

    Path existingWorkspace(String repositoryUrl) {
        return existingWorkspace(repositoryUrl, null);
    }

    Path existingWorkspace(String repositoryUrl, String workspaceKey) {
        Path workspace = workspacePath(repositoryUrl, workspaceKey);
        if (!Files.isDirectory(workspace.resolve(".git"))) {
            throw new IllegalStateException("No workspace for repository — check out or create a branch first: "
                    + repositoryUrl);
        }
        return workspace;
    }

    /** Rejects paths that end up outside the workspace clone or inside .git. */
    static Path insideWorkspace(Path workspace, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        Path target = workspace.resolve(relativePath).normalize();
        if (!target.startsWith(workspace) || target.equals(workspace)
                || target.startsWith(workspace.resolve(".git"))) {
            throw new IllegalArgumentException(
                    "File path must stay inside the repository workspace: " + relativePath);
        }
        return target;
    }

    Path workspacePath(String repositoryUrl) {
        return workspacePath(repositoryUrl, null);
    }

    Path workspacePath(String repositoryUrl, String workspaceKey) {
        Path base = Path.of(properties.getWorkspaceBasePath());
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return base.resolve(repositoryHash(repositoryUrl));
        }
        return base.resolve("runs").resolve(workspaceKey).resolve(repositoryHash(repositoryUrl));
    }

    static String repositoryHash(String repositoryUrl) {
        return UUID.nameUUIDFromBytes(repositoryUrl.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 16);
    }

    static CredentialsProvider credentials(String username, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String user = username != null && !username.isBlank() ? username : "token";
        return new UsernamePasswordCredentialsProvider(user, token);
    }

    private GitToolResult failure(String tool, Exception e) {
        log.warn("{} failed: {}", tool, e.getMessage());
        return GitToolResult.failure(tool + " failed: " + e.getMessage());
    }
}
