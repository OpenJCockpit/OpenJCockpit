package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitConnectivityServiceTest {

    private ProjectRepository projectRepo;
    private ProjectGitCredentialRepository credRepo;
    private CredentialEncryptionService encryptionService;
    private JGitPort jGitPort;
    private GitHubPort gitHubPort;
    private GitConnectivityService service;

    @BeforeEach
    void setUp() {
        projectRepo = mock(ProjectRepository.class);
        credRepo = mock(ProjectGitCredentialRepository.class);
        encryptionService = mock(CredentialEncryptionService.class);
        jGitPort = mock(JGitPort.class);
        gitHubPort = mock(GitHubPort.class);
        service = new GitConnectivityService(projectRepo, credRepo, encryptionService, 5, jGitPort, gitHubPort);
        when(projectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void checkProjectGitAccess_throwsNotFound_whenProjectDoesNotExist() {
        var id = UUID.randomUUID();
        when(projectRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkProjectGitAccess(id))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void checkProjectGitAccess_returnsUnknown_whenGitUrlIsNull() {
        var id = UUID.randomUUID();
        var project = project(id, null);
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.UNKNOWN);
    }

    @Test
    void checkProjectGitAccess_returnsUnknown_whenGitUrlIsBlank() {
        var id = UUID.randomUUID();
        var project = project(id, "   ");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.UNKNOWN);
    }

    @Test
    void checkProjectGitAccess_returnsAccessible_whenAnonJGitSucceeds() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.com/org/repo");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        // jGitPort.checkReachable does nothing (success)
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_returnsNotAccessible_whenAnonFailsAndNoCredentials() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.com/org/private");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("auth required")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.NOT_ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_returnsNotAccessible_whenCredentialHasNullSecret() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://example.com/repo");
        var cred = new ProjectGitCredential();
        cred.setProjectId(id);
        cred.setEncryptedSecret(null);
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        doThrow(new RuntimeException("connection refused")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.NOT_ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_returnsCheckFailed_whenDecryptionThrows() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://example.com/repo");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        doThrow(new RuntimeException("network error")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        when(encryptionService.decrypt("encrypted-cipher")).thenThrow(new RuntimeException("decryption failed"));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.CHECK_FAILED);
    }

    @Test
    void checkProjectGitAccess_returnsAccessible_whenJGitSucceedsWithCredentials() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://example.com/repo");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("plaintext-token");
        doThrow(new RuntimeException("auth required")).doNothing()
                .when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_returnsNotAccessible_whenBothAnonAndCredentialsFail_nonGitHub() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://gitlab.com/org/repo");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("plaintext-token");
        doThrow(new RuntimeException("not found 404")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.NOT_ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_usesGitHubApi_whenJGitFailsWithCredentialsAndGitHubUrl() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.com/owner/repo");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("ghp_token");
        doThrow(new RuntimeException("auth required")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        // gitHubPort succeeds (no exception)
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_returnsCheckFailed_whenGitHubApiAlsoFails() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.com/owner/repo");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("ghp_token");
        doThrow(new RuntimeException("auth required")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        doThrow(new RuntimeException("GitHub 404")).when(gitHubPort).checkRepository(any(), any(), any(), any());
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.CHECK_FAILED);
    }

    @Test
    void checkProjectGitAccess_returnsNotAccessible_whenGitHubUrlUnparseable() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.com/");
        var cred = credential(id, "encrypted-cipher");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("token");
        doThrow(new RuntimeException("auth required")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.NOT_ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_usesCustomGitHubApiUrl_whenSet() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://github.example.com/owner/repo");
        var cred = credential(id, "encrypted-cipher");
        cred.setGithubApiUrl("https://github.example.com/api/v3");
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("token");
        doThrow(new RuntimeException("auth")).when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.ACCESSIBLE);
    }

    @Test
    void checkProjectGitAccess_usesTokenAsUsername_whenUsernameIsNull() throws Exception {
        var id = UUID.randomUUID();
        var project = project(id, "https://example.com/repo");
        var cred = credential(id, "encrypted-cipher");
        cred.setUsername(null);
        when(projectRepo.findById(id)).thenReturn(Optional.of(project));
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(cred));
        when(encryptionService.decrypt("encrypted-cipher")).thenReturn("my-token");
        doThrow(new RuntimeException("auth required")).doNothing()
                .when(jGitPort).checkReachable(any(), any(), any(int.class));
        assertThat(service.checkProjectGitAccess(id)).isEqualTo(GitStatus.ACCESSIBLE);
    }

    @Test
    void isGitHubUrl_detectsGitHubDotCom() {
        assertThat(service.isGitHubUrl("https://github.com/org/repo")).isTrue();
    }

    @Test
    void isGitHubUrl_detectsSshGitHub() {
        assertThat(service.isGitHubUrl("git@github.com:org/repo.git")).isTrue();
    }

    @Test
    void isGitHubUrl_detectsGitHubEnterprise() {
        assertThat(service.isGitHubUrl("https://github.example.com/org/repo")).isTrue();
    }

    @Test
    void isGitHubUrl_returnsFalseForNonGitHub() {
        assertThat(service.isGitHubUrl("https://gitlab.com/org/repo")).isFalse();
    }

    @Test
    void isGitHubUrl_returnsFalseForNull() {
        assertThat(service.isGitHubUrl(null)).isFalse();
    }

    @Test
    void parseGitHubOwnerRepo_parsesHttpsUrl() {
        var result = service.parseGitHubOwnerRepo("https://github.com/myorg/myrepo");
        assertThat(result).containsExactly("myorg", "myrepo");
    }

    @Test
    void parseGitHubOwnerRepo_parsesHttpsUrlWithDotGit() {
        var result = service.parseGitHubOwnerRepo("https://github.com/myorg/myrepo.git");
        assertThat(result).containsExactly("myorg", "myrepo");
    }

    @Test
    void parseGitHubOwnerRepo_parsesSshUrl() {
        var result = service.parseGitHubOwnerRepo("git@github.com:myorg/myrepo.git");
        assertThat(result).containsExactly("myorg", "myrepo");
    }

    @Test
    void parseGitHubOwnerRepo_returnsNull_forUnparseable() {
        assertThat(service.parseGitHubOwnerRepo("https://github.com/")).isNull();
    }

    @Test
    void friendlyMessage_returnsGenericForNullMessage() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException((String) null)))
                .isEqualTo("Git check failed");
    }

    @Test
    void friendlyMessage_returnsTimeoutMessageForTimeoutKeyword() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("Connection timeout occurred")))
                .isEqualTo("Connection timed out");
    }

    @Test
    void friendlyMessage_returnsAuthMessageForAuthKeyword() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("auth required")))
                .isEqualTo("Authentication failed");
    }

    @Test
    void friendlyMessage_returnsNotFoundMessageForNotFoundKeyword() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("repository not found")))
                .isEqualTo("Repository not found");
    }

    @Test
    void friendlyMessage_returns404MessageFor404Keyword() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("HTTP 404 error")))
                .isEqualTo("Repository not found");
    }

    @Test
    void friendlyMessage_returnsDetailedMessageForOtherErrors() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("SSL handshake failed")))
                .isEqualTo("Git check failed: SSL handshake failed");
    }

    private Project project(UUID id, String gitUrl) {
        var p = new Project();
        p.setId(id);
        p.setName("Test");
        p.setGitUrl(gitUrl);
        p.setActive((short) 1);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private ProjectGitCredential credential(UUID projectId, String encryptedSecret) {
        var c = new ProjectGitCredential();
        c.setProjectId(projectId);
        c.setEncryptedSecret(encryptedSecret);
        c.setUsername("user");
        c.setActive((short) 1);
        return c;
    }
}