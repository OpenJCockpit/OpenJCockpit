package nl.metafactory.gitmcp.git;

import nl.metafactory.gitmcp.config.GitAdapterConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-18: real-JGit proof that git_create_branch can branch off a base branch
 * that only exists as origin/&lt;base&gt; on a persistent workspace clone that
 * was created with a different setBranch(...) — including a base that was only
 * pushed to origin after cloning. Uses the real production bean
 * ({@link GitAdapterConfig#gitWorkspaceOperations()}) and the real
 * {@link GitToolsService} against a {@code file://} origin under {@link TempDir}.
 * Not a single mock of {@link GitWorkspaceOperations}.
 */
class GitToolsServiceRealGitTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path tempDir;

    private Path bareOriginDir;
    private String repoUrl;
    private GitToolsService service;

    @BeforeEach
    void setUp() throws Exception {
        Path workspacesDir = Files.createDirectories(tempDir.resolve("workspaces"));
        bareOriginDir = Files.createDirectories(tempDir.resolve("bare-origin.git"));
        Path workingOrigin = Files.createDirectories(tempDir.resolve("working-origin"));

        try (Git bare = Git.init().setDirectory(bareOriginDir.toFile()).setBare(true)
                .setInitialBranch("main").call()) {
            assertThat(bare).isNotNull();
        }
        repoUrl = "file://" + bareOriginDir.toAbsolutePath();

        try (Git workGit = Git.init().setDirectory(workingOrigin.toFile())
                .setInitialBranch("main").call()) {
            commitFile(workGit, workingOrigin, "a.txt", "hello");

            workGit.checkout().setCreateBranch(true).setName("develop").call();
            commitFile(workGit, workingOrigin, "b.txt", "world");

            workGit.checkout().setName("main").call();
            workGit.remoteAdd().setName("origin").setUri(new URIish(repoUrl)).call();
            workGit.push().setRemote("origin").setRefSpecs(
                    new RefSpec("refs/heads/main:refs/heads/main"),
                    new RefSpec("refs/heads/develop:refs/heads/develop")).call();
        }

        GitWorkspaceOperations gitOps = new GitAdapterConfig().gitWorkspaceOperations();
        GitToolsProperties properties = new GitToolsProperties();
        properties.setWorkspaceBasePath(workspacesDir.toString());
        properties.setTimeoutSeconds(30);
        service = new GitToolsService(gitOps, properties);
    }

    private static void commitFile(Git git, Path repoRoot, String name, String content) throws Exception {
        Files.writeString(repoRoot.resolve(name), content);
        git.add().addFilepattern(name).call();
        git.commit().setMessage("Add " + name).setAuthor(IDENT).setCommitter(IDENT).call();
    }

    @Test
    void baseExistedAtCloneTime() {
        var established = service.createBranch(repoUrl, "main", "spec/wf-1", null, null);
        assertThat(established.success()).isTrue();

        var result = service.createBranch(repoUrl, "develop", "spec/wf-2", null, null);

        assertThat(result.success()).isTrue();
        Path workspace = Path.of(result.workspace());
        assertThat(Files.exists(workspace.resolve("a.txt"))).isTrue();
        assertThat(Files.exists(workspace.resolve("b.txt"))).isTrue();
    }

    @Test
    void basePushedToOriginAfterTheClone() throws Exception {
        var established = service.createBranch(repoUrl, "main", "spec/wf-1", null, null);
        assertThat(established.success()).isTrue();

        Path scratch = tempDir.resolve("scratch");
        try (Git scratchGit = Git.cloneRepository().setURI(repoUrl)
                .setDirectory(scratch.toFile()).setBranch("main").call()) {
            scratchGit.checkout().setCreateBranch(true).setName("later/feature").call();
            commitFile(scratchGit, scratch, "c.txt", "later");
            scratchGit.push().setRemote("origin")
                    .setRefSpecs(new RefSpec("refs/heads/later/feature:refs/heads/later/feature")).call();
        }

        var result = service.createBranch(repoUrl, "later/feature", "spec/wf-3", null, null);

        assertThat(result.success()).isTrue();
        Path workspace = Path.of(result.workspace());
        assertThat(Files.exists(workspace.resolve("c.txt"))).isTrue();
    }

    @Test
    void repeatCallSameBaseDifferentNewBranchWithoutRecreate() {
        assertThat(service.createBranch(repoUrl, "main", "spec/wf-1", null, null).success()).isTrue();
        assertThat(service.createBranch(repoUrl, "develop", "spec/wf-2", null, null).success()).isTrue();
        assertThat(service.createBranch(repoUrl, "develop", "spec/wf-3", null, null).success()).isTrue();
    }

    @Test
    void baseEqualsCloneBranchStillWorks() {
        var result = service.createBranch(repoUrl, "main", "spec/reg", null, null);

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(Path.of(result.workspace()).resolve("a.txt"))).isTrue();
    }

    @Test
    void baseThatExistsNowhereFailsNamingTheBranch() {
        assertThat(service.createBranch(repoUrl, "main", "spec/wf-1", null, null).success()).isTrue();

        var result = service.createBranch(repoUrl, "no-such-base", "spec/z", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("no-such-base");
    }

    @Test
    void concurrentParallelRunsWithDistinctWorkspaceKeysDoNotCorruptEachOthersWorkingTree() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> taskA = () -> runConcurrentScenario("run-a", "spec/run-a", "run-a.txt",
                    "content from run a", "Add run-a file", "Run A", "run-a@example.com");
            Callable<Boolean> taskB = () -> runConcurrentScenario("run-b", "spec/run-b", "run-b.txt",
                    "content from run b", "Add run-b file", "Run B", "run-b@example.com");

            List<Future<Boolean>> futures = executor.invokeAll(List.of(taskA, taskB));

            assertThat(futures.get(0).get()).isTrue();
            assertThat(futures.get(1).get()).isTrue();

            Path scratchA = tempDir.resolve("scratch-run-a");
            try (Git scratchGitA = Git.cloneRepository().setURI(repoUrl)
                    .setDirectory(scratchA.toFile()).setBranch("spec/run-a").call()) {
                assertThat(scratchGitA).isNotNull();
            }
            assertThat(Files.exists(scratchA.resolve("run-a.txt"))).isTrue();
            assertThat(Files.exists(scratchA.resolve("run-b.txt"))).isFalse();

            Path scratchB = tempDir.resolve("scratch-run-b");
            try (Git scratchGitB = Git.cloneRepository().setURI(repoUrl)
                    .setDirectory(scratchB.toFile()).setBranch("spec/run-b").call()) {
                assertThat(scratchGitB).isNotNull();
            }
            assertThat(Files.exists(scratchB.resolve("run-b.txt"))).isTrue();
            assertThat(Files.exists(scratchB.resolve("run-a.txt"))).isFalse();
        } finally {
            executor.shutdown();
        }
    }

    private boolean runConcurrentScenario(String workspaceKey, String branch, String fileName,
                                          String content, String commitMessage,
                                          String authorName, String authorEmail) {
        try {
            if (!service.createBranch(repoUrl, "main", branch, null, null, workspaceKey).success()) {
                return false;
            }
            if (!service.writeFile(repoUrl, fileName, content, workspaceKey).success()) {
                return false;
            }
            if (!service.commit(repoUrl, commitMessage, authorName, authorEmail, workspaceKey).success()) {
                return false;
            }
            return service.push(repoUrl, branch, null, null, workspaceKey).success();
        } catch (Exception e) {
            return false;
        }
    }
}
