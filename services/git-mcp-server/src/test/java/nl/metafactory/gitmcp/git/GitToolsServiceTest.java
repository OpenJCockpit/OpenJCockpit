package nl.metafactory.gitmcp.git;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitToolsServiceTest {

    private static final String REPO_URL = "https://github.com/org/repo.git";

    private RecordingGitOps gitOps;
    private GitToolsProperties properties;
    private GitToolsService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        gitOps = new RecordingGitOps();
        properties = new GitToolsProperties();
        properties.setWorkspaceBasePath(tempDir.toString());
        properties.setTimeoutSeconds(45);
        service = new GitToolsService(gitOps, properties);
    }

    private Path workspace() {
        return service.workspacePath(REPO_URL);
    }

    private void givenExistingWorkspace() throws Exception {
        Files.createDirectories(workspace().resolve(".git"));
    }

    // ── git_checkout_branch ─────────────────────────────────────────────────

    @Test
    void checkoutBranchClonesWhenWorkspaceMissing() {
        var result = service.checkoutBranch(REPO_URL, "main", "user", "secret", null);

        assertThat(result.success()).isTrue();
        assertThat(result.branch()).isEqualTo("main");
        assertThat(result.workspace()).isEqualTo(workspace().toString());
        assertThat(gitOps.calls).contains("clone:" + REPO_URL + ":main:45");
        // clone already on the correct branch — no extra checkout
        assertThat(gitOps.calls).noneMatch(c -> c.startsWith("checkout:"));
    }

    @Test
    void checkoutBranchSwitchesWhenWorkspaceOnOtherBranch() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";

        var result = service.checkoutBranch(REPO_URL, "feature/x", null, null, null);

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).contains("checkout:feature/x:create=false");
        assertThat(gitOps.calls).noneMatch(c -> c.startsWith("clone:"));
    }

    @Test
    void checkoutBranchReturnsFailureOnGitError() {
        gitOps.failWith = new IllegalStateException("clone broken");

        var result = service.checkoutBranch(REPO_URL, "main", null, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_checkout_branch failed").contains("clone broken");
    }

    // ── git_create_branch ───────────────────────────────────────────────────

    @Test
    void createBranchPullsBaseBranchAndCreatesNewBranch() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";

        var result = service.createBranch(REPO_URL, "main", "spec/001-login", "user", "secret");

        assertThat(result.success()).isTrue();
        assertThat(result.branch()).isEqualTo("spec/001-login");
        assertThat(gitOps.calls).containsSubsequence(
                "ensureLocal:main",
                "pull:45",
                "checkout:spec/001-login:create=true");
    }

    @Test
    void createBranchChecksOutBaseBranchFirstWhenOnAnotherBranch() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "spec/000-old";

        var result = service.createBranch(REPO_URL, "main", "spec/001-login", null, null);

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).containsSubsequence(
                "ensureLocal:main",
                "checkout:main:create=false",
                "pull:45",
                "checkout:spec/001-login:create=true");
    }

    @Test
    void createBranchFailsWhenBaseBranchNotFoundLocallyOrOnOrigin() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";
        gitOps.ensureLocalBranchResult = false;

        var result = service.createBranch(REPO_URL, "develop", "spec/001-login", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message())
                .contains("git_create_branch failed: base branch not found locally or on origin: develop");
        assertThat(gitOps.calls).contains("ensureLocal:develop");
        assertThat(gitOps.calls).noneMatch(c -> c.startsWith("pull:"));
        assertThat(gitOps.calls).noneMatch(c -> c.equals("checkout:spec/001-login:create=true"));
    }

    @Test
    void createBranchReturnsFailureOnGitError() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";
        gitOps.failWith = new IllegalStateException("push rejected");

        var result = service.createBranch(REPO_URL, "main", "spec/001-login", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_create_branch failed");
    }

    // ── git_pull ────────────────────────────────────────────────────────────

    @Test
    void pullUpdatesTheRequestedBranch() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";

        var result = service.pull(REPO_URL, "main", null, null);

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).contains("pull:45");
    }

    @Test
    void pullChecksOutBranchWhenOnAnotherBranch() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "other";

        var result = service.pull(REPO_URL, "main", null, null);

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).containsSubsequence("checkout:main:create=false", "pull:45");
    }

    @Test
    void pullReturnsFailureOnGitError() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "main";
        gitOps.failWith = new IllegalStateException("remote unreachable");

        var result = service.pull(REPO_URL, "main", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_pull failed");
    }

    // ── git_commit ──────────────────────────────────────────────────────────

    @Test
    void commitStagesAllAndReturnsCommitHash() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "spec/001-login";
        gitOps.hasChanges = true;

        var result = service.commit(REPO_URL, "Add login spec", "Ricky", "ricky@example.com");

        assertThat(result.success()).isTrue();
        assertThat(result.commitHash()).isEqualTo("hash-1");
        assertThat(result.branch()).isEqualTo("spec/001-login");
        assertThat(gitOps.calls).containsSubsequence("stageAll", "commit:Add login spec:Ricky:ricky@example.com");
    }

    @Test
    void commitUsesDefaultAuthorWhenNotProvided() throws Exception {
        givenExistingWorkspace();
        gitOps.hasChanges = true;

        var result = service.commit(REPO_URL, "msg", "", null);

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).contains("commit:msg:Agentic Workflow:agentic@metafactory.nl");
    }

    @Test
    void commitFailsWhenNothingChanged() throws Exception {
        givenExistingWorkspace();
        gitOps.hasChanges = false;

        var result = service.commit(REPO_URL, "msg", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Nothing to commit");
    }

    @Test
    void commitFailsWithoutWorkspace() {
        var result = service.commit(REPO_URL, "msg", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No workspace for repository");
    }

    @Test
    void commitReturnsFailureOnGitError() throws Exception {
        givenExistingWorkspace();
        gitOps.hasChanges = true;
        gitOps.failWith = new IllegalStateException("commit broken");

        var result = service.commit(REPO_URL, "msg", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_commit failed");
    }

    // ── git_list_files / git_read_file ──────────────────────────────────────

    @Test
    void listFilesReturnsRelativePathsWithoutGitInternals() throws Exception {
        givenExistingWorkspace();
        Files.createDirectories(workspace().resolve("src"));
        Files.writeString(workspace().resolve("src/App.java"), "class App {}");
        Files.writeString(workspace().resolve("README.md"), "# Readme");
        Files.writeString(workspace().resolve(".git").resolve("config"), "internal");

        var result = service.listFiles(REPO_URL, null);

        assertThat(result.success()).isTrue();
        assertThat(result.files()).containsExactly("README.md", "src/App.java");
    }

    @Test
    void listFilesCanBeLimitedToASubdirectory() throws Exception {
        givenExistingWorkspace();
        Files.createDirectories(workspace().resolve("src"));
        Files.writeString(workspace().resolve("src/App.java"), "class App {}");
        Files.writeString(workspace().resolve("README.md"), "# Readme");

        var result = service.listFiles(REPO_URL, "src");

        assertThat(result.success()).isTrue();
        assertThat(result.files()).containsExactly("src/App.java");
    }

    @Test
    void listFilesFailsForMissingDirectoryOrWorkspace() throws Exception {
        var noWorkspace = service.listFiles(REPO_URL, null);
        assertThat(noWorkspace.success()).isFalse();
        assertThat(noWorkspace.message()).contains("No workspace for repository");

        givenExistingWorkspace();
        var missingDir = service.listFiles(REPO_URL, "does-not-exist");
        assertThat(missingDir.success()).isFalse();
        assertThat(missingDir.message()).contains("Not a directory");

        var traversal = service.listFiles(REPO_URL, "../elders");
        assertThat(traversal.success()).isFalse();
    }

    @Test
    void readFileReturnsContentAndTruncatesLargeFiles() throws Exception {
        givenExistingWorkspace();
        Files.writeString(workspace().resolve("small.txt"), "contents");
        Files.writeString(workspace().resolve("groot.txt"),
                "x".repeat(GitToolsService.MAX_FILE_CHARS + 10));

        var small = service.readFile(REPO_URL, "small.txt");
        var large = service.readFile(REPO_URL, "groot.txt");

        assertThat(small.success()).isTrue();
        assertThat(small.path()).isEqualTo("small.txt");
        assertThat(small.content()).isEqualTo("contents");
        assertThat(large.content()).hasSize(GitToolsService.MAX_FILE_CHARS);
    }

    @Test
    void readFileFailsForMissingFileWorkspaceOrTraversal() throws Exception {
        var noWorkspace = service.readFile(REPO_URL, "x.txt");
        assertThat(noWorkspace.success()).isFalse();

        givenExistingWorkspace();
        var missing = service.readFile(REPO_URL, "does-not-exist.txt");
        assertThat(missing.success()).isFalse();
        assertThat(missing.message()).contains("File not found");

        var traversal = service.readFile(REPO_URL, "../secret.txt");
        assertThat(traversal.success()).isFalse();
        assertThat(traversal.message()).contains("git_read_file failed");
    }

    @Test
    void fileResultFactories() {
        var listOk = GitFileListResult.ok("done", List.of("a.txt"));
        assertThat(listOk.success()).isTrue();
        assertThat(listOk.files()).containsExactly("a.txt");
        assertThat(GitFileListResult.failure("boom").files()).isNull();

        var fileOk = GitFileResult.ok("done", "a.txt", "contents");
        assertThat(fileOk.success()).isTrue();
        assertThat(fileOk.path()).isEqualTo("a.txt");
        assertThat(fileOk.content()).isEqualTo("contents");
        assertThat(GitFileResult.failure("boom").content()).isNull();
    }

    // ── git_write_file ──────────────────────────────────────────────────────

    @Test
    void writeFileCreatesFileWithParentDirectories() throws Exception {
        givenExistingWorkspace();
        gitOps.currentBranch = "spec/001-login";

        var result = service.writeFile(REPO_URL, "specs/spec-001/spec.md", "# Login spec");

        assertThat(result.success()).isTrue();
        assertThat(result.branch()).isEqualTo("spec/001-login");
        assertThat(workspace().resolve("specs/spec-001/spec.md")).hasContent("# Login spec");
    }

    @Test
    void writeFileTreatsNullContentAsEmpty() throws Exception {
        givenExistingWorkspace();

        var result = service.writeFile(REPO_URL, "specs/empty.md", null);

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(workspace().resolve("specs/empty.md"))).isEmpty();
    }

    @Test
    void writeFileFailsWithoutWorkspace() {
        var result = service.writeFile(REPO_URL, "specs/spec.md", "content");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No workspace for repository");
    }

    @Test
    void writeFileRejectsPathsOutsideTheWorkspace() throws Exception {
        givenExistingWorkspace();

        var traversal = service.writeFile(REPO_URL, "../evil.md", "x");
        var gitInternals = service.writeFile(REPO_URL, ".git/config", "x");
        var workspaceItself = service.writeFile(REPO_URL, ".", "x");

        assertThat(traversal.success()).isFalse();
        assertThat(traversal.message()).contains("must stay inside the repository workspace");
        assertThat(gitInternals.success()).isFalse();
        assertThat(workspaceItself.success()).isFalse();
    }

    @Test
    void writeFileRejectsMissingPath() throws Exception {
        givenExistingWorkspace();

        var nullPath = service.writeFile(REPO_URL, null, "x");
        var blankPath = service.writeFile(REPO_URL, "  ", "x");

        assertThat(nullPath.success()).isFalse();
        assertThat(nullPath.message()).contains("File path is required");
        assertThat(blankPath.success()).isFalse();
    }

    // ── git_push ────────────────────────────────────────────────────────────

    @Test
    void pushSendsBranchToOrigin() throws Exception {
        givenExistingWorkspace();

        var result = service.push(REPO_URL, "spec/001-login", "user", "secret");

        assertThat(result.success()).isTrue();
        assertThat(gitOps.calls).contains("push:origin:spec/001-login:45");
    }

    @Test
    void pushFailsWithoutWorkspace() {
        var result = service.push(REPO_URL, "main", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No workspace for repository");
    }

    @Test
    void pushReturnsFailureOnGitError() throws Exception {
        givenExistingWorkspace();
        gitOps.failWith = new IllegalStateException("no write permissions");

        var result = service.push(REPO_URL, "main", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("git_push failed");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @Test
    void credentialsAreNullWithoutTokenAndDefaultUsernameWithToken() {
        assertThat(GitToolsService.credentials(null, null)).isNull();
        assertThat(GitToolsService.credentials("user", "  ")).isNull();
        assertThat(GitToolsService.credentials(null, "secret"))
                .isInstanceOf(UsernamePasswordCredentialsProvider.class);
        assertThat(GitToolsService.credentials("", "secret")).isNotNull();
        assertThat(GitToolsService.credentials("user", "secret")).isNotNull();
    }

    @Test
    void workspacePathIsStablePerRepositoryUrl() {
        assertThat(service.workspacePath(REPO_URL)).isEqualTo(service.workspacePath(REPO_URL));
        assertThat(service.workspacePath(REPO_URL))
                .isNotEqualTo(service.workspacePath("https://github.com/org/other.git"));
        assertThat(GitToolsService.repositoryHash(REPO_URL)).hasSize(16);
    }

    @Test
    void ensureWorkspaceReusesExistingClone() throws Exception {
        givenExistingWorkspace();

        var workspace = service.ensureWorkspace(REPO_URL, "main", null, null);

        assertThat(workspace).isEqualTo(workspace());
        assertThat(gitOps.calls).isEmpty();
    }

    @Test
    void existingWorkspaceThrowsWithClearMessageWhenMissing() {
        assertThatThrownBy(() -> service.existingWorkspace(REPO_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("check out or create a branch first");
    }

    // ── workspaceKey validation ─────────────────────────────────────────────

    @Test
    void workspaceKeyValidationAcceptsBlankAndValidKeysRejectsInvalidOnes() {
        assertThat(GitToolsService.isValidWorkspaceKey(null)).isTrue();
        assertThat(GitToolsService.isValidWorkspaceKey("")).isTrue();
        assertThat(GitToolsService.isValidWorkspaceKey("run-a")).isTrue();
        assertThat(GitToolsService.isValidWorkspaceKey("a".repeat(64))).isTrue();
        assertThat(GitToolsService.isValidWorkspaceKey("../etc")).isFalse();
        assertThat(GitToolsService.isValidWorkspaceKey("/etc/passwd")).isFalse();
        assertThat(GitToolsService.isValidWorkspaceKey("a".repeat(65))).isFalse();
        assertThat(GitToolsService.isValidWorkspaceKey("-abc")).isFalse();
    }

    @Test
    void blankOrNullWorkspaceKeyResolvesToLegacyPath() {
        assertThat(service.workspacePath(REPO_URL, null)).isEqualTo(service.workspacePath(REPO_URL));
        assertThat(service.workspacePath(REPO_URL, "")).isEqualTo(service.workspacePath(REPO_URL));
        assertThat(service.workspacePath(REPO_URL, "some-key")).isNotEqualTo(service.workspacePath(REPO_URL));
    }

    @Test
    void checkoutBranchRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.checkoutBranch(REPO_URL, "main", null, null, "../etc");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
        assertThat(Files.notExists(workspace().resolve(".git"))).isTrue();
    }

    @Test
    void createBranchRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.createBranch(REPO_URL, "main", "spec/x", null, null, "-bad");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
        assertThat(Files.notExists(workspace().resolve(".git"))).isTrue();
    }

    @Test
    void gitToolResultFactories() {
        var ok = GitToolResult.ok("done", "/ws", "main", "abc");
        assertThat(ok.success()).isTrue();
        assertThat(ok.message()).isEqualTo("done");
        assertThat(ok.workspace()).isEqualTo("/ws");
        assertThat(ok.branch()).isEqualTo("main");
        assertThat(ok.commitHash()).isEqualTo("abc");

        var failure = GitToolResult.failure("boom");
        assertThat(failure.success()).isFalse();
        assertThat(failure.message()).isEqualTo("boom");
        assertThat(failure.workspace()).isNull();
    }

    /** Recording fake so that every tool is tested against the port without real git. */
    private static class RecordingGitOps implements GitWorkspaceOperations {
        final List<String> calls = new ArrayList<>();
        String currentBranch = "main";
        boolean hasChanges = false;
        boolean ensureLocalBranchResult = true;
        RuntimeException failWith;

        private void maybeFail() {
            if (failWith != null) throw failWith;
        }

        @Override
        public void cloneRepository(String url, String branch, Path target,
                                    CredentialsProvider credentials, int timeoutSeconds) {
            maybeFail();
            calls.add("clone:" + url + ":" + branch + ":" + timeoutSeconds);
            try {
                Files.createDirectories(target.resolve(".git"));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            currentBranch = branch;
        }

        @Override
        public void checkoutBranch(Path repoPath, String branchName, boolean createBranch) {
            maybeFail();
            calls.add("checkout:" + branchName + ":create=" + createBranch);
            currentBranch = branchName;
        }

        @Override
        public void pull(Path repoPath, CredentialsProvider credentials, int timeoutSeconds) {
            maybeFail();
            calls.add("pull:" + timeoutSeconds);
        }

        @Override
        public boolean ensureLocalBranch(Path repoPath, String branch,
                                         CredentialsProvider credentials, int timeoutSeconds) {
            maybeFail();
            calls.add("ensureLocal:" + branch);
            return ensureLocalBranchResult;
        }

        @Override
        public boolean hasUncommittedChanges(Path repoPath) {
            return hasChanges;
        }

        @Override
        public void stageAll(Path repoPath) {
            maybeFail();
            calls.add("stageAll");
        }

        @Override
        public String commitWithMessage(Path repoPath, String message, String authorName, String authorEmail) {
            maybeFail();
            calls.add("commit:" + message + ":" + authorName + ":" + authorEmail);
            return "hash-1";
        }

        @Override
        public void pushBranch(Path repoPath, String remote, String branch,
                               CredentialsProvider credentials, int timeoutSeconds) {
            maybeFail();
            calls.add("push:" + remote + ":" + branch + ":" + timeoutSeconds);
        }

        @Override
        public String currentBranch(Path repoPath) {
            return currentBranch;
        }
    }

    @Test
    void existingWorkspaceOneArgumentOverloadReturnsWorkspaceWhenPresent() throws Exception {
        givenExistingWorkspace();

        var result = service.existingWorkspace(REPO_URL);

        assertThat(result).isEqualTo(workspace());
    }

    @Test
    void pullRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.pull(REPO_URL, "main", null, null, "../bad");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }

    @Test
    void commitRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.commit(REPO_URL, "msg", null, null, "/abs/path");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }

    @Test
    void listFilesRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.listFiles(REPO_URL, null, "bad key with spaces");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }

    @Test
    void readFileRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.readFile(REPO_URL, "a.txt", "..");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }

    @Test
    void writeFileRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.writeFile(REPO_URL, "a.txt", "content",
                "toolongkeytoolongkeytoolongkeytoolongkeytoolongkeytoolongkeytoolongkey");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }

    @Test
    void pushRejectsInvalidWorkspaceKeyBeforeAnyFilesystemAccess() {
        var result = service.push(REPO_URL, "main", null, null, "***");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("workspaceKey");
        assertThat(Files.notExists(workspace())).isTrue();
    }
}
