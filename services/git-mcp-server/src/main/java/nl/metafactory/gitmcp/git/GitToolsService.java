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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The git integration as MCP tools, reached through the LiteLLM gateway (mcp_servers.openjcockpit_git).
 * Each tool receives the repository URL and an optional workspace key and works in one workspace clone
 * per repository (and per key) under the configured base path. Credentials are never tool arguments:
 * a keyed workspace (e.g. {@code job-<uuid>} of a git-workspace job) gets them server-side from
 * {@link GitCredentialResolver}; the unkeyed shared workspace is anonymous.
 */
@Service
public class GitToolsService {

    private static final Logger log = LoggerFactory.getLogger(GitToolsService.class);
    static final int MAX_LISTED_FILES = 500;
    static final int MAX_FILE_CHARS = 100_000;

    private static final String PARAM_REPOSITORY_URL = "HTTPS or file URL of the project git repository";
    private static final String PARAM_WORKSPACE_KEY = "Optional opaque key isolating this caller's workspace clone "
            + "from other concurrent runs against the same repository. Omit for the shared workspace.";

    private static final Pattern WORKSPACE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    static boolean isValidWorkspaceKey(String workspaceKey) {
        return workspaceKey == null || workspaceKey.isBlank() || WORKSPACE_KEY_PATTERN.matcher(workspaceKey).matches();
    }

    private final GitWorkspaceOperations gitOps;
    private final GitToolsProperties properties;
    private final GitCredentialResolver credentialResolver;

    /** Anonymous access only (no keyed-workspace credentials); used by tests. */
    GitToolsService(GitWorkspaceOperations gitOps, GitToolsProperties properties) {
        this(gitOps, properties, (workspaceKey, repositoryUrl) -> java.util.Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GitToolsService(GitWorkspaceOperations gitOps, GitToolsProperties properties,
                           GitCredentialResolver credentialResolver) {
        this.gitOps = gitOps;
        this.properties = properties;
        this.credentialResolver = credentialResolver;
    }

    GitToolResult checkoutBranch(
            String repositoryUrl,
            String branch,
            String username,
            String token,
            String workspaceKey) {
        return run("git_checkout_branch", workspaceKey, GitToolResult::failure, () -> {
            Path workspace = ensureWorkspace(repositoryUrl, branch, username, token, workspaceKey);
            checkoutIfNotCurrent(workspace, branch);
            return GitToolResult.ok("Checked out branch " + branch, workspace.toString(), branch, null);
        });
    }

    GitToolResult createBranch(String repositoryUrl, String baseBranch, String newBranch, String username, String token) {
        return createBranch(repositoryUrl, baseBranch, newBranch, username, token, null);
    }

    GitToolResult createBranch(
            String repositoryUrl,
            String baseBranch,
            String newBranch,
            String username,
            String token,
            String workspaceKey) {
        return run("git_create_branch", workspaceKey, GitToolResult::failure, () -> {
            Path workspace = ensureWorkspace(repositoryUrl, baseBranch, username, token, workspaceKey);
            if (!gitOps.ensureLocalBranch(workspace, baseBranch, credentials(username, token),
                    properties.getTimeoutSeconds())) {
                return GitToolResult.failure(
                        "git_create_branch failed: base branch not found locally or on origin: " + baseBranch);
            }
            checkoutIfNotCurrent(workspace, baseBranch);
            gitOps.pull(workspace, credentials(username, token), properties.getTimeoutSeconds());
            gitOps.checkoutBranch(workspace, newBranch, true);
            return GitToolResult.ok("Created branch " + newBranch + " from " + baseBranch,
                    workspace.toString(), newBranch, null);
        });
    }

    GitToolResult pull(String repositoryUrl, String branch, String username, String token) {
        return pull(repositoryUrl, branch, username, token, null);
    }

    GitToolResult pull(
            String repositoryUrl,
            String branch,
            String username,
            String token,
            String workspaceKey) {
        return run("git_pull", workspaceKey, GitToolResult::failure, () -> {
            Path workspace = ensureWorkspace(repositoryUrl, branch, username, token, workspaceKey);
            checkoutIfNotCurrent(workspace, branch);
            gitOps.pull(workspace, credentials(username, token), properties.getTimeoutSeconds());
            return GitToolResult.ok("Pulled latest changes for " + branch, workspace.toString(), branch, null);
        });
    }

    GitToolResult commit(String repositoryUrl, String message, String authorName, String authorEmail) {
        return commit(repositoryUrl, message, authorName, authorEmail, null);
    }

    @Tool(name = "git_commit", description = "Stage all changes in the repository workspace and commit them "
            + "with the given message. Returns the commit hash.")
    public GitToolResult commit(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Commit message") String message,
            @ToolParam(description = "Author name for the commit", required = false) String authorName,
            @ToolParam(description = "Author email for the commit", required = false) String authorEmail,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return run("git_commit", workspaceKey, GitToolResult::failure, () -> {
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
        });
    }

    GitFileListResult listFiles(String repositoryUrl, String subdirectory) {
        return listFiles(repositoryUrl, subdirectory, null);
    }

    @Tool(name = "git_list_files", description = "List the file paths (relative to the repository root) in "
            + "the repository workspace, optionally limited to a subdirectory. Check out or create a branch first.")
    public GitFileListResult listFiles(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Subdirectory to list, relative to the repository root; empty for the whole repository",
                    required = false) String subdirectory,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return run("git_list_files", workspaceKey, GitFileListResult::failure, () -> {
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
        });
    }

    GitFileResult readFile(String repositoryUrl, String path) {
        return readFile(repositoryUrl, path, null);
    }

    @Tool(name = "git_read_file", description = "Read the text content of a file at a path relative to the "
            + "repository root. Check out or create a branch first.")
    public GitFileResult readFile(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "File path relative to the repository root, e.g. src/main/App.java") String path,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return run("git_read_file", workspaceKey, GitFileResult::failure, () -> {
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
        });
    }

    GitToolResult writeFile(String repositoryUrl, String path, String content) {
        return writeFile(repositoryUrl, path, content, null);
    }

    @Tool(name = "git_write_file", description = "Write text content to a file at a path relative to the "
            + "repository root, e.g. specs/spec-001.md. Creates parent directories as needed. Check out or "
            + "create a branch first; commit and push afterwards.")
    public GitToolResult writeFile(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "File path relative to the repository root, e.g. specs/spec-001.md") String path,
            @ToolParam(description = "Full text content of the file") String content,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return run("git_write_file", workspaceKey, GitToolResult::failure, () -> {
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            Path target = insideWorkspace(workspace, path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content != null ? content : "");
            return GitToolResult.ok("Wrote file " + path, workspace.toString(),
                    gitOps.currentBranch(workspace), null);
        });
    }

    GitToolResult push(String repositoryUrl, String branch, String username, String token) {
        return push(repositoryUrl, branch, username, token, null);
    }

    GitToolResult push(
            String repositoryUrl,
            String branch,
            String username,
            String token,
            String workspaceKey) {
        return run("git_push", workspaceKey, GitToolResult::failure, () -> {
            Path workspace = existingWorkspace(repositoryUrl, workspaceKey);
            gitOps.pushBranch(workspace, "origin", branch, credentials(username, token),
                    properties.getTimeoutSeconds());
            return GitToolResult.ok("Pushed branch " + branch + " to origin", workspace.toString(), branch, null);
        });
    }

    // ── MCP tools: credentials are resolved server-side per workspace, never passed as arguments ──

    @Tool(name = "git_checkout_branch", description = "Check out an existing branch of the project repository. "
            + "Clones the repository into the server-managed workspace when needed.")
    public GitToolResult checkoutBranchTool(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Branch to check out") String branch,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return withCredentials("git_checkout_branch", workspaceKey, repositoryUrl,
                c -> checkoutBranch(repositoryUrl, branch, c.username(), c.token(), workspaceKey));
    }

    @Tool(name = "git_create_branch", description = "Create and check out a new branch from a base branch of the "
            + "project repository, e.g. for a new spec or implementation change.")
    public GitToolResult createBranchTool(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Base branch to branch off from, e.g. main") String baseBranch,
            @ToolParam(description = "Name of the new branch, e.g. spec/001-feature-slug") String newBranch,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return withCredentials("git_create_branch", workspaceKey, repositoryUrl,
                c -> createBranch(repositoryUrl, baseBranch, newBranch, c.username(), c.token(), workspaceKey));
    }

    @Tool(name = "git_pull", description = "Pull the latest changes for the currently checked out branch "
            + "of the project repository.")
    public GitToolResult pullTool(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Branch that should be current before pulling") String branch,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return withCredentials("git_pull", workspaceKey, repositoryUrl,
                c -> pull(repositoryUrl, branch, c.username(), c.token(), workspaceKey));
    }

    @Tool(name = "git_push", description = "Push a branch of the repository workspace to origin so a "
            + "pull request can be opened.")
    public GitToolResult pushTool(
            @ToolParam(description = PARAM_REPOSITORY_URL) String repositoryUrl,
            @ToolParam(description = "Branch to push to origin") String branch,
            @ToolParam(description = PARAM_WORKSPACE_KEY, required = false) String workspaceKey) {
        return withCredentials("git_push", workspaceKey, repositoryUrl,
                c -> push(repositoryUrl, branch, c.username(), c.token(), workspaceKey));
    }

    /**
     * Resolves the workspace's credentials server-side and runs the operation with them. A keyed
     * workspace whose credentials cannot be resolved fails closed; an unkeyed one is anonymous.
     */
    private GitToolResult withCredentials(String tool, String workspaceKey, String repositoryUrl,
                                          Function<GitCredentialResolver.GitCredentials, GitToolResult> operation) {
        if (!isValidWorkspaceKey(workspaceKey)) {
            return GitToolResult.failure("invalid workspaceKey");
        }
        GitCredentialResolver.GitCredentials credentials;
        try {
            credentials = credentialResolver.resolve(workspaceKey, repositoryUrl)
                    .orElse(new GitCredentialResolver.GitCredentials(null, null));
        } catch (RuntimeException e) {
            log.warn("{} denied: workspace credentials unavailable ({})", tool, e.getMessage());
            return GitToolResult.failure(tool + " failed: workspace credentials unavailable");
        }
        return operation.apply(credentials);
    }

    /** Validates the workspace key, runs the tool body and turns any exception into a failure result. */
    private <R> R run(String tool, String workspaceKey, Function<String, R> failure, ToolBody<R> body) {
        if (!isValidWorkspaceKey(workspaceKey)) {
            return failure.apply("invalid workspaceKey");
        }
        try {
            return body.call();
        } catch (Exception e) {
            log.warn("{} failed: {}", tool, e.getMessage());
            return failure.apply(tool + " failed: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ToolBody<R> {
        R call() throws Exception;
    }

    private void checkoutIfNotCurrent(Path workspace, String branch) throws Exception {
        if (!branch.equals(gitOps.currentBranch(workspace))) {
            gitOps.checkoutBranch(workspace, branch, false);
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
}
