package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static nl.metafactory.aicontrol.model.GitCredentialType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitOperationServiceTest {

    private GitWorkspaceOperations ops;
    private CredentialEncryptionService encryption;
    private GitOperationService service;

    @BeforeEach
    void setUp() {
        ops = mock(GitWorkspaceOperations.class);
        encryption = mock(CredentialEncryptionService.class);
        var props = new AgenticWorkflowProperties();
        props.setCloneTimeoutSeconds(10);
        props.setPushTimeoutSeconds(10);
        service = new GitOperationService(ops, encryption, props);
    }

    @Test
    void cloneDefaultBranchDelegatesWithNullCredsWhenNone() throws Exception {
        service.cloneDefaultBranch("https://github.com/org/repo", "main",
                Path.of("/tmp/repo"), null);
        verify(ops).cloneRepository(eq("https://github.com/org/repo"), eq("main"),
                eq(Path.of("/tmp/repo")), isNull(), eq(10));
    }

    @Test
    void cloneDefaultBranchThrowsGitCloneFailedOnException() throws Exception {
        doThrow(new RuntimeException("network error"))
                .when(ops).cloneRepository(any(), any(), any(), any(), anyInt());
        assertThatThrownBy(() ->
                service.cloneDefaultBranch("https://example.com/repo", "main",
                        Path.of("/tmp/repo"), null))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("network error")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED));
    }

    @Test
    void checkoutNewBranchDelegates() throws Exception {
        service.checkoutNewBranch(Path.of("/tmp/repo"), "agentic/test/20260703-main");
        verify(ops).createAndCheckoutBranch(Path.of("/tmp/repo"), "agentic/test/20260703-main");
    }

    @Test
    void checkoutNewBranchThrowsBranchCreateFailed() throws Exception {
        doThrow(new RuntimeException()).when(ops).createAndCheckoutBranch(any(), any());
        assertThatThrownBy(() -> service.checkoutNewBranch(Path.of("/tmp"), "branch"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.BRANCH_CREATE_FAILED));
    }

    @Test
    void hasChangesReturnsTrueWhenDirty() throws Exception {
        when(ops.hasUncommittedChanges(any())).thenReturn(true);
        assertThat(service.hasChanges(Path.of("/tmp/repo"))).isTrue();
    }

    @Test
    void hasChangesThrowsGitCloneFailedOnException() throws Exception {
        doThrow(new RuntimeException("io error")).when(ops).hasUncommittedChanges(any());
        assertThatThrownBy(() -> service.hasChanges(Path.of("/tmp/repo")))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED));
    }

    @Test
    void stageAllDelegates() throws Exception {
        service.stageAll(Path.of("/tmp/repo"));
        verify(ops).stageAll(Path.of("/tmp/repo"));
    }

    @Test
    void stageAllThrowsCommitFailedOnException() throws Exception {
        doThrow(new RuntimeException("io error")).when(ops).stageAll(any());
        assertThatThrownBy(() -> service.stageAll(Path.of("/tmp/repo")))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.COMMIT_FAILED));
    }

    @Test
    void commitReturnsHash() throws Exception {
        when(ops.commitWithMessage(any(), any(), any(), any())).thenReturn("abc123");
        String hash = service.commit(Path.of("/tmp"), "msg", "Author", "a@b.nl");
        assertThat(hash).isEqualTo("abc123");
    }

    @Test
    void commitThrowsCommitFailedOnException() throws Exception {
        doThrow(new RuntimeException("io error")).when(ops).commitWithMessage(any(), any(), any(), any());
        assertThatThrownBy(() -> service.commit(Path.of("/tmp"), "msg", "Author", "a@b.nl"))
                .isInstanceOf(GitWorkspaceException.class)
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.COMMIT_FAILED));
    }

    @Test
    void pushBranchWithNullCredentialPassesNullToPort() throws Exception {
        service.pushBranch(Path.of("/tmp/repo"), "origin", "agentic/x", null);
        verify(ops).pushBranch(any(), eq("origin"), eq("agentic/x"), isNull(), eq(10));
    }

    @Test
    void pushBranchThrowsPushFailedWithRootCauseInMessage() throws Exception {
        doThrow(new RuntimeException("transport failed",
                new IllegalStateException("Authentication is required")))
                .when(ops).pushBranch(any(), any(), any(), any(), anyInt());
        assertThatThrownBy(() -> service.pushBranch(Path.of("/tmp/repo"), "origin", "agentic/x", null))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("Authentication is required")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.PUSH_FAILED));
    }

    @Test
    void rootMessageMasksCredentialsEmbeddedInCauseMessage() {
        var cause = new RuntimeException("https://user:token@github.com/org/repo: 403 Forbidden");
        assertThat(service.rootMessage(cause))
                .isEqualTo("https://***@github.com/org/repo: 403 Forbidden");
    }

    @Test
    void rootMessageFallsBackToClassNameWhenMessageMissing() {
        assertThat(service.rootMessage(new IllegalStateException()))
                .isEqualTo("IllegalStateException");
    }

    @Test
    void listRemoteBranchesDelegatesAndReturnsBranchNames() throws Exception {
        when(ops.listRemoteBranches("https://github.com/org/repo", null, 10))
                .thenReturn(java.util.List.of("main", "spec-init/test/20260705-abc"));

        assertThat(service.listRemoteBranches("https://github.com/org/repo", null))
                .containsExactly("main", "spec-init/test/20260705-abc");
    }

    @Test
    void listRemoteBranchesThrowsRepositoryUnavailableOnException() throws Exception {
        doThrow(new RuntimeException("connection refused"))
                .when(ops).listRemoteBranches(any(), any(), anyInt());

        assertThatThrownBy(() -> service.listRemoteBranches("https://github.com/org/repo", null))
                .isInstanceOf(GitWorkspaceException.class)
                .hasMessageContaining("connection refused")
                .satisfies(e -> assertThat(((GitWorkspaceException) e).getErrorCode())
                        .isEqualTo(GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE));
    }

    @Test
    void buildCredentialsReturnsNullForNoneType() {
        var cred = new ProjectGitCredential();
        cred.setCredentialType(NONE);
        CredentialsProvider result = service.buildCredentials(cred);
        assertThat(result).isNull();
    }

    @Test
    void buildCredentialsBuildsProviderForHttpsToken() {
        when(encryption.decrypt("enc-token")).thenReturn("plain-token");
        var cred = new ProjectGitCredential();
        cred.setCredentialType(HTTPS_TOKEN);
        cred.setUsername("user");
        cred.setEncryptedSecret("enc-token");
        CredentialsProvider result = service.buildCredentials(cred);
        assertThat(result).isInstanceOf(UsernamePasswordCredentialsProvider.class);
    }

    @Test
    void buildCredentialsUsesTokenAsDefaultUsername() {
        when(encryption.decrypt(any())).thenReturn("pat-value");
        var cred = new ProjectGitCredential();
        cred.setCredentialType(GITHUB_PAT);
        cred.setEncryptedSecret("enc");
        CredentialsProvider result = service.buildCredentials(cred);
        assertThat(result).isInstanceOf(UsernamePasswordCredentialsProvider.class);
    }

    @Test
    void buildCredentialsUsesEmptyPasswordWhenSecretMissing() {
        var cred = new ProjectGitCredential();
        cred.setCredentialType(HTTPS_TOKEN);
        cred.setUsername("user");

        CredentialsProvider result = service.buildCredentials(cred);

        assertThat(result).isInstanceOf(UsernamePasswordCredentialsProvider.class);
        verifyNoInteractions(encryption);
    }

    @Test
    void maskUrlRemovesEmbeddedCredentials() {
        assertThat(service.maskUrl("https://user:token@github.com/org/repo"))
                .isEqualTo("https://***@github.com/org/repo");
    }

    @Test
    void maskUrlPassesThroughCleanUrl() {
        assertThat(service.maskUrl("https://github.com/org/repo"))
                .isEqualTo("https://github.com/org/repo");
    }

    @Test
    void maskUrlReturnsPlaceholderForNullUrl() {
        assertThat(service.maskUrl(null)).isEqualTo("<null>");
    }

    @Test
    void getHeadCommitHashReturnsHashOnSuccess() throws Exception {
        when(ops.getHeadCommitHash(any())).thenReturn("deadbeef");
        assertThat(service.getHeadCommitHash(Path.of("/tmp/repo"))).isEqualTo("deadbeef");
    }

    @Test
    void getHeadCommitHashReturnsNullOnError() throws Exception {
        when(ops.getHeadCommitHash(any())).thenThrow(new RuntimeException("no repo"));
        assertThat(service.getHeadCommitHash(Path.of("/noexist"))).isNull();
    }
}