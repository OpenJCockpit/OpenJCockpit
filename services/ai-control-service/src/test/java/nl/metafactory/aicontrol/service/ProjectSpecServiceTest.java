package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecInitResult;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSpecServiceTest {

    private ProjectRepository projectRepo;
    private ProjectGitCredentialRepository credRepo;
    private GitOperationService gitOps;
    private AgenticWorkflowProperties properties;
    private ProjectSpecService service;

    private Path workspaceBase;
    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        projectRepo = mock(ProjectRepository.class);
        credRepo = mock(ProjectGitCredentialRepository.class);
        gitOps = mock(GitOperationService.class);

        properties = new AgenticWorkflowProperties();
        workspaceBase = tempDir;
        properties.setWorkspaceBasePath(tempDir.toString());
        properties.setDefaultBaseBranch("main");

        service = new ProjectSpecService(projectRepo, credRepo, gitOps,
                new BranchNameGenerator(properties), properties);

        projectId = UUID.randomUUID();
        project = new Project();
        project.setName("Test Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo.git");
        project.setDefaultBranch("main");
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1))
                .thenReturn(Optional.empty());
    }

    private void cloneCreates(FilesWriter writer) {
        doAnswer(invocation -> {
            Path repoDir = invocation.getArgument(2);
            Files.createDirectories(repoDir);
            writer.write(repoDir);
            return null;
        }).when(gitOps).cloneDefaultBranch(anyString(), anyString(), any(Path.class), any());
    }

    private ProjectGitCredential givenActiveCredential() {
        var credential = new ProjectGitCredential();
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1))
                .thenReturn(Optional.of(credential));
        return credential;
    }

    @FunctionalInterface
    private interface FilesWriter {
        void write(Path repoDir) throws Exception;
    }

    // ── listSpecFiles ────────────────────────────────────────────────────────

    @Test
    void listSpecFilesReturnsEmptyWhenSpecFolderMissing() {
        cloneCreates(repoDir -> {});
        assertThat(service.listSpecFiles(projectId)).isEmpty();
    }

    @Test
    void listSpecFilesReturnsEmptyWhenSpecFolderEmpty() {
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve("specs")));
        assertThat(service.listSpecFiles(projectId)).isEmpty();
    }

    @Test
    void listSpecFilesReturnsSortedFilesWithFirstSelected() {
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.writeString(specsDir.resolve("zeta.md"), "# zeta");
            Files.writeString(specsDir.resolve("alpha.md"), "# alpha");
        });

        List<SpecFile> specs = service.listSpecFiles(projectId);

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).id()).isEqualTo("alpha");
        assertThat(specs.get(0).fileName()).isEqualTo("alpha.md");
        assertThat(specs.get(0).selected()).isTrue();
        assertThat(specs.get(0).status()).isEqualTo("Active");
        assertThat(specs.get(0).content()).isEqualTo("# alpha");
        assertThat(specs.get(0).repositoryUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(specs.get(0).lastChanged()).isNotBlank();
        assertThat(specs.get(1).id()).isEqualTo("zeta");
        assertThat(specs.get(1).selected()).isFalse();
        assertThat(specs.get(1).status()).isEqualTo("Open");
    }

    @Test
    void listSpecFilesReturnsSpecPerFeatureFolder() {
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir.resolve("002-second-feature"));
            Files.createDirectories(specsDir.resolve("001-example-feature"));
            Files.writeString(specsDir.resolve("001-example-feature/spec.md"), "# first");
            Files.writeString(specsDir.resolve("002-second-feature/spec.md"), "# second");
        });

        List<SpecFile> specs = service.listSpecFiles(projectId);

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).id()).isEqualTo("001-example-feature");
        assertThat(specs.get(0).fileName()).isEqualTo("001-example-feature/spec.md");
        assertThat(specs.get(0).content()).isEqualTo("# first");
        assertThat(specs.get(0).selected()).isTrue();
        assertThat(specs.get(1).fileName()).isEqualTo("002-second-feature/spec.md");
        assertThat(specs.get(1).selected()).isFalse();
    }

    @Test
    void listSpecFilesSkipsIndexNonMarkdownAndFoldersWithoutSpec() {
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir.resolve("empty-folder"));
            Files.createDirectories(specsDir.resolve("with-spec"));
            Files.writeString(specsDir.resolve("with-spec/spec.md"), "# feature");
            Files.writeString(specsDir.resolve("_index.md"), "# index");
            Files.writeString(specsDir.resolve("notes.txt"), "not markdown");
        });

        List<SpecFile> specs = service.listSpecFiles(projectId);

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).fileName()).isEqualTo("with-spec/spec.md");
    }

    @Test
    void listSpecFilesCleansUpWorkspaceAfterListing() throws Exception {
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve("specs")));
        service.listSpecFiles(projectId);
        try (var entries = Files.list(workspaceBase)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    void listSpecFilesUsesDefaultBaseBranchWhenProjectBranchBlank() {
        project.setDefaultBranch("  ");
        cloneCreates(repoDir -> {});
        service.listSpecFiles(projectId);
        verify(gitOps).cloneDefaultBranch(eq("https://github.com/org/repo.git"), eq("main"),
                any(Path.class), isNull());
    }

    @Test
    void listSpecFilesWrapsUnreadableFilesInUncheckedIOException() {
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.write(specsDir.resolve("broken.md"), new byte[]{(byte) 0xC3, (byte) 0x28});
        });

        assertThatThrownBy(() -> service.listSpecFiles(projectId))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void listSpecFilesThrowsNoSelectedProjectForNullId() {
        assertThatThrownBy(() -> service.listSpecFiles(null))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT));
    }

    @Test
    void listSpecFilesThrowsProjectNotFoundForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(projectRepo.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listSpecFiles(unknown))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void listSpecFilesThrowsProjectInactive() {
        project.setActive((short) 0);
        assertThatThrownBy(() -> service.listSpecFiles(projectId))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PROJECT_INACTIVE));
    }

    @Test
    void listSpecFilesThrowsGitUrlMissingWhenNull() {
        project.setGitUrl(null);
        assertThatThrownBy(() -> service.listSpecFiles(projectId))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    @Test
    void listSpecFilesThrowsGitUrlMissingWhenBlank() {
        project.setGitUrl("  ");
        assertThatThrownBy(() -> service.listSpecFiles(projectId))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_URL_MISSING));
    }

    // ── initSpecFolder ───────────────────────────────────────────────────────

    @Test
    void initSpecFolderCreatesBranchSpecTemplateCommitAndPush() {
        var credential = givenActiveCredential();
        cloneCreates(repoDir -> {});
        AtomicBoolean templateExistedAtStage = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Path repoDir = invocation.getArgument(0);
            templateExistedAtStage.set(Files.exists(repoDir.resolve(".specify/templates/spec-template.md")));
            return null;
        }).when(gitOps).stageAll(any(Path.class));
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc123");

        SpecInitResult result = service.initSpecFolder(projectId, "ricky");

        assertThat(templateExistedAtStage).isTrue();
        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.branch()).startsWith("spec-init/test-project/");
        assertThat(result.baseBranch()).isEqualTo("main");
        assertThat(result.commitHash()).isEqualTo("abc123");
        assertThat(result.fileName()).isEqualTo("spec-template.md");
        assertThat(result.pullRequestUrl())
                .isEqualTo("https://github.com/org/repo/compare/main..." + result.branch() + "?expand=1");
        assertThat(result.message()).contains(result.branch());
        verify(gitOps).checkoutNewBranch(any(Path.class), eq(result.branch()));
        verify(gitOps).pushBranch(any(Path.class), eq("origin"), eq(result.branch()), same(credential));
    }

    @Test
    void initSpecFolderFailsFastWithoutCredentialsForHttpRemote() {
        assertThatThrownBy(() -> service.initSpecFolder(projectId, "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("Configure credentials")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_AUTH_FAILED));
        verify(gitOps, never()).cloneDefaultBranch(anyString(), anyString(), any(Path.class), any());
    }

    @Test
    void initSpecFolderAllowsMissingCredentialsForNonHttpRemote() {
        project.setGitUrl("file:///srv/git/repo.git");
        cloneCreates(repoDir -> {});
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc123");

        SpecInitResult result = service.initSpecFolder(projectId, "ricky");

        assertThat(result.commitHash()).isEqualTo("abc123");
        assertThat(result.pullRequestUrl()).isNull();
        verify(gitOps).pushBranch(any(Path.class), eq("origin"), eq(result.branch()), isNull());
    }

    @Test
    void initSpecFolderCommitMessageIncludesRequester() {
        givenActiveCredential();
        cloneCreates(repoDir -> {});
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc123");

        service.initSpecFolder(projectId, "ricky");

        verify(gitOps).commit(any(Path.class),
                org.mockito.ArgumentMatchers.contains("Requested-by: ricky"),
                eq("Agentic Workflow"), eq("agentic@metafactory.nl"));
    }

    @Test
    void initSpecFolderThrowsNoChangesWhenSpecTemplateAlreadyExists() {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path templateDir = repoDir.resolve(".specify/templates");
            Files.createDirectories(templateDir);
            Files.writeString(templateDir.resolve("spec-template.md"), "# existing template");
        });

        assertThatThrownBy(() -> service.initSpecFolder(projectId, "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.NO_CHANGES));
    }

    @Test
    void initSpecFolderThrowsSpecFileInvalidWhenTemplateCannotBeWritten() {
        givenActiveCredential();
        cloneCreates(repoDir -> Files.writeString(repoDir.resolve(".specify"), "not a directory"));

        assertThatThrownBy(() -> service.initSpecFolder(projectId, "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void initSpecFolderCleansUpWorkspaceOnFailure() throws Exception {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path templateDir = repoDir.resolve(".specify/templates");
            Files.createDirectories(templateDir);
            Files.writeString(templateDir.resolve("spec-template.md"), "# existing template");
        });
        assertThatThrownBy(() -> service.initSpecFolder(projectId, "ricky"))
                .isInstanceOf(GitWorkspaceException.class);
        try (var entries = Files.list(workspaceBase)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    void initSpecFolderThrowsNoChangesWhenSpecInitBranchAlreadyOnRemote() {
        givenActiveCredential();
        when(gitOps.listRemoteBranches(eq("https://github.com/org/repo.git"), any()))
                .thenReturn(List.of("main", "spec-init/test-project/20260704-aaaa1111"));

        assertThatThrownBy(() -> service.initSpecFolder(projectId, "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("spec-init/test-project/20260704-aaaa1111")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.NO_CHANGES));
        verify(gitOps, never()).cloneDefaultBranch(anyString(), anyString(), any(Path.class), any());
    }

    // ── saveSpecFile ─────────────────────────────────────────────────────────

    @Test
    void saveSpecFileWritesContentCommitsAndPushesNewBranch() {
        var credential = givenActiveCredential();
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.writeString(specsDir.resolve("pricing-rules.md"), "# old content");
        });
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("def456");

        SpecInitResult result = service.saveSpecFile(projectId, "pricing-rules.md", "# new content", "ricky");

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.branch()).startsWith("spec-edit/test-project/");
        assertThat(result.baseBranch()).isEqualTo("main");
        assertThat(result.commitHash()).isEqualTo("def456");
        assertThat(result.fileName()).isEqualTo("pricing-rules.md");
        assertThat(result.pullRequestUrl())
                .isEqualTo("https://github.com/org/repo/compare/main..." + result.branch() + "?expand=1");
        verify(gitOps).checkoutNewBranch(any(Path.class), eq(result.branch()));
        verify(gitOps).pushBranch(any(Path.class), eq("origin"), eq(result.branch()), same(credential));
    }

    @Test
    void saveSpecFileWrapsIoExceptionFromWriteFailure() {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.writeString(specsDir.resolve("pricing-rules.md"), "# old content");
        });
        // Replace the already-validated target file with a directory of the same name right before the
        // write, so Files.writeString(...) fails with an IOException regardless of OS/user permissions
        // (a permission-bit trick like chmod/setReadable is not reliable when the build runs as root).
        doAnswer(invocation -> {
            Path repoDir = invocation.getArgument(0);
            Path targetFile = repoDir.resolve("specs").resolve("pricing-rules.md");
            Files.delete(targetFile);
            Files.createDirectory(targetFile);
            return null;
        }).when(gitOps).checkoutNewBranch(any(Path.class), anyString());

        assertThatThrownBy(() -> service.saveSpecFile(projectId, "pricing-rules.md", "# new content", "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("Failed to write spec file")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
        verify(gitOps, never()).commit(any(Path.class), anyString(), anyString(), anyString());
        verify(gitOps, never()).pushBranch(any(Path.class), anyString(), anyString(), any());
    }

    @Test
    void saveSpecFileWritesContentToFeatureFolderSpec() {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path featureDir = repoDir.resolve("specs/001-example-feature");
            Files.createDirectories(featureDir);
            Files.writeString(featureDir.resolve("spec.md"), "# old");
        });
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc");

        SpecInitResult result = service.saveSpecFile(
                projectId, "001-example-feature/spec.md", "# new", "ricky");

        assertThat(result.fileName()).isEqualTo("001-example-feature/spec.md");
    }

    @Test
    void saveSpecFileRejectsPathTraversalOutsideSpecsFolder() {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Files.createDirectories(repoDir.resolve("specs"));
            Files.writeString(repoDir.resolve("secret.txt"), "top secret");
        });

        assertThatThrownBy(() -> service.saveSpecFile(projectId, "../secret.txt", "pwned", "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
        verify(gitOps, never()).checkoutNewBranch(any(Path.class), anyString());
        verify(gitOps, never()).pushBranch(any(Path.class), anyString(), anyString(), any());
    }

    @Test
    void saveSpecFileRejectsUnknownFile() {
        givenActiveCredential();
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve("specs")));

        assertThatThrownBy(() -> service.saveSpecFile(projectId, "does-not-exist.md", "content", "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
        verify(gitOps, never()).checkoutNewBranch(any(Path.class), anyString());
    }

    @Test
    void saveSpecFileRejectsBlankFileName() {
        givenActiveCredential();
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve("specs")));

        assertThatThrownBy(() -> service.saveSpecFile(projectId, "  ", "content", "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void saveSpecFileFailsFastWithoutCredentialsForHttpRemote() {
        assertThatThrownBy(() -> service.saveSpecFile(projectId, "pricing-rules.md", "content", "ricky"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_AUTH_FAILED));
        verify(gitOps, never()).cloneDefaultBranch(anyString(), anyString(), any(Path.class), any());
    }

    @Test
    void saveSpecFileCommitMessageIncludesRequesterAndFileName() {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.writeString(specsDir.resolve("pricing-rules.md"), "# old");
        });
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc123");

        service.saveSpecFile(projectId, "pricing-rules.md", "# new", "ricky");

        verify(gitOps).commit(any(Path.class),
                org.mockito.ArgumentMatchers.argThat(msg ->
                        msg.contains("Requested-by: ricky") && msg.contains("pricing-rules.md")),
                eq("Agentic Workflow"), eq("agentic@metafactory.nl"));
    }

    @Test
    void saveSpecFileCleansUpWorkspaceAfterSaving() throws Exception {
        givenActiveCredential();
        cloneCreates(repoDir -> {
            Path specsDir = repoDir.resolve("specs");
            Files.createDirectories(specsDir);
            Files.writeString(specsDir.resolve("pricing-rules.md"), "# old");
        });
        when(gitOps.commit(any(Path.class), anyString(), anyString(), anyString())).thenReturn("abc123");

        service.saveSpecFile(projectId, "pricing-rules.md", "# new", "ricky");

        try (var entries = Files.list(workspaceBase)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    // ── specInitStatus ───────────────────────────────────────────────────────

    @Test
    void specInitStatusReturnsNoneWhenNoSpecInitBranchExists() {
        cloneCreates(repoDir -> {});
        when(gitOps.listRemoteBranches(eq("https://github.com/org/repo.git"), any()))
                .thenReturn(List.of("main", "feature/other"));

        var status = service.specInitStatus(projectId);

        assertThat(status.pending()).isFalse();
        assertThat(status.branch()).isNull();
        assertThat(status.branchUrl()).isNull();
        assertThat(status.pullRequestUrl()).isNull();
        assertThat(status.templateExists()).isFalse();
    }

    @Test
    void specInitStatusReportsExistingSpecifyFolderOnBaseBranch() {
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve(".specify/templates")));
        when(gitOps.listRemoteBranches(eq("https://github.com/org/repo.git"), any()))
                .thenReturn(List.of("main"));

        var status = service.specInitStatus(projectId);

        assertThat(status.pending()).isFalse();
        assertThat(status.templateExists()).isTrue();
    }

    @Test
    void specInitStatusReportsSpecifyFolderEvenWithPendingBranch() {
        cloneCreates(repoDir -> Files.createDirectories(repoDir.resolve(".specify")));
        when(gitOps.listRemoteBranches(eq("https://github.com/org/repo.git"), any()))
                .thenReturn(List.of("spec-init/test-project/20260705-bbbb2222"));

        var status = service.specInitStatus(projectId);

        assertThat(status.pending()).isTrue();
        assertThat(status.templateExists()).isTrue();
    }

    @Test
    void specInitStatusReturnsLatestPendingBranchWithLinks() {
        when(gitOps.listRemoteBranches(eq("https://github.com/org/repo.git"), any()))
                .thenReturn(List.of(
                        "spec-init/test-project/20260701-aaaa1111",
                        "main",
                        "spec-init/test-project/20260705-bbbb2222",
                        "spec-init/other-project/20260706-cccc3333"));

        cloneCreates(repoDir -> {});

        var status = service.specInitStatus(projectId);

        assertThat(status.pending()).isTrue();
        assertThat(status.templateExists()).isFalse();
        assertThat(status.branch()).isEqualTo("spec-init/test-project/20260705-bbbb2222");
        assertThat(status.branchUrl())
                .isEqualTo("https://github.com/org/repo/tree/spec-init/test-project/20260705-bbbb2222");
        assertThat(status.pullRequestUrl())
                .isEqualTo("https://github.com/org/repo/compare/main...spec-init/test-project/20260705-bbbb2222?expand=1");
    }

    @Test
    void specInitStatusOmitsLinksForNonHttpRemote() {
        project.setGitUrl("git@github.com:org/repo.git");
        when(gitOps.listRemoteBranches(eq("git@github.com:org/repo.git"), any()))
                .thenReturn(List.of("spec-init/test-project/20260705-bbbb2222"));
        cloneCreates(repoDir -> {});

        var status = service.specInitStatus(projectId);

        assertThat(status.pending()).isTrue();
        assertThat(status.branchUrl()).isNull();
        assertThat(status.pullRequestUrl()).isNull();
    }

    @Test
    void branchUrlStripsGitSuffixAndIsNullForNonHttp() {
        assertThat(service.branchUrl("https://github.com/org/repo.git", "b"))
                .isEqualTo("https://github.com/org/repo/tree/b");
        assertThat(service.branchUrl("https://github.com/org/repo", "b"))
                .isEqualTo("https://github.com/org/repo/tree/b");
        assertThat(service.branchUrl("git@github.com:org/repo.git", "b")).isNull();
        assertThat(service.branchUrl(null, "b")).isNull();
    }

    @Test
    void specTemplateContentComesFromClasspathResource() {
        String content = service.specTemplateContent();
        assertThat(content).startsWith("---");
        assertThat(content).contains("workflow_id: WF-001");
        assertThat(content).contains("# Feature Spec: {{feature_name}}");
        assertThat(content).contains("clean_architecture_boundary_check");
    }

    @Test
    void specTemplateContentThrowsSpecFileInvalidWhenResourceMissing() {
        ProjectSpecService broken = new ProjectSpecService(projectRepo, credRepo, gitOps,
                new BranchNameGenerator(properties), properties) {
            @Override
            InputStream openSpecTemplateResource() {
                return null;
            }
        };

        assertThatThrownBy(broken::specTemplateContent)
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining(ProjectSpecService.SPEC_TEMPLATE_RESOURCE)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void specTemplateContentWrapsResourceReadFailure() {
        ProjectSpecService broken = new ProjectSpecService(projectRepo, credRepo, gitOps,
                new BranchNameGenerator(properties), properties) {
            @Override
            InputStream openSpecTemplateResource() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("broken");
                    }
                };
            }
        };

        assertThatThrownBy(broken::specTemplateContent)
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.SPEC_FILE_INVALID));
    }

    @Test
    void pullRequestUrlIsNullForNonHttpRemotes() {
        assertThat(service.pullRequestUrl("git@github.com:org/repo.git", "main", "branch")).isNull();
        assertThat(service.pullRequestUrl(null, "main", "branch")).isNull();
    }

    @Test
    void pullRequestUrlKeepsUrlWithoutGitSuffix() {
        assertThat(service.pullRequestUrl("https://github.com/org/repo", "main", "b"))
                .isEqualTo("https://github.com/org/repo/compare/main...b?expand=1");
    }

    @Test
    void cleanupFailureIsSwallowedWhenWorkspaceDisappeared() {
        cloneCreates(repoDir -> {
            Files.delete(repoDir);
            Files.delete(repoDir.getParent());
        });

        assertThat(service.listSpecFiles(projectId)).isEmpty();
    }

    @Test
    void createWorkDirFailureIsWrappedInUncheckedIOException() throws Exception {
        Path blocked = workspaceBase.resolve("blocked");
        Files.writeString(blocked, "file, not a directory");
        properties.setWorkspaceBasePath(blocked.toString());

        assertThatThrownBy(() -> service.listSpecFiles(projectId))
                .isInstanceOf(UncheckedIOException.class);
    }
}
