package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void friendlyMessage_returnsTlsMessageForCertificateErrors() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("SSL handshake failed")))
                .isEqualTo("TLS certificate error");
    }

    @Test
    void friendlyMessage_neverEchoesRawExceptionText() {
        assertThat(GitConnectivityService.friendlyMessage(new RuntimeException("connect to 10.0.0.5:8080 refused")))
                .isEqualTo("Git check failed");
    }

    @Test
    void checkGitAccess_rejectsUnsupportedScheme_withoutContactingTheHost() throws Exception {
        var result = service.checkGitAccess("file:///etc", null);
        assertThat(result.status()).isEqualTo(GitStatus.NOT_ACCESSIBLE);
        assertThat(result.message()).isEqualTo("Unsupported URL: use https, ssh or git");
        verify(jGitPort, never()).checkReachable(any(), any(), anyInt());
    }

    @Test
    void checkGitConnection_usesPlainSecret_andReturnsNoWarningsForPublicHost() throws Exception {
        doThrow(new RuntimeException("auth required")).doNothing()
                .when(jGitPort).checkReachable(eq("https://example.com/org/repo"), any(), anyInt());

        var check = service.checkGitConnection("https://example.com/org/repo", "bob", "s3cret", null);

        assertThat(check.result().status()).isEqualTo(GitStatus.ACCESSIBLE);
        assertThat(check.result().message()).isEqualTo("Reachable with credentials");
        verifyNoInteractions(encryptionService, projectRepo, credRepo);
    }

    @Test
    void checkGitConnection_flagsHostThatResolvesToLoopback_butStillChecks() throws Exception {
        var check = service.checkGitConnection("https://localhost/org/repo", null, null, null);

        assertThat(check.warnings()).containsExactly("host_loopback");
        assertThat(check.result().status()).isEqualTo(GitStatus.ACCESSIBLE);
        verify(jGitPort).checkReachable(eq("https://localhost/org/repo"), any(), anyInt());
    }

    @Test
    void checkGitConnection_flagsGitHubApiHost() {
        var check = service.checkGitConnection("https://example.com/org/repo", "u", "s", "https://127.0.0.1/api");
        assertThat(check.warnings()).containsExactly("host_loopback");
    }

    @Test
    void checkGitConnection_rejectsBadSchemes() throws Exception {
        assertThatThrownBy(() -> service.checkGitConnection("http://example.com/r", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.checkGitConnection("https://example.com/r", "u", "s", "http://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jGitPort, never()).checkReachable(any(), any(), anyInt());
    }

    @Test
    void checkGitConnection_neverReturnsRawExceptionText() throws Exception {
        doThrow(new RuntimeException("connect to 10.0.0.5:9999 refused"))
                .when(jGitPort).checkReachable(any(), any(), anyInt());
        var check = service.checkGitConnection("https://example.com/r", null, null, null);
        assertThat(check.result().message()).isEqualTo("Git check failed").doesNotContain("10.0.0.5");
    }

    @Test
    void checkGitConnection_writesAuditLineWithoutSecretOrFullUrl() {
        var audit = (Logger) LoggerFactory.getLogger("nl.metafactory.aicontrol.audit.GitCheck");
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        audit.addAppender(appender);
        try {
            service.checkGitConnection("https://localhost/org/private-repo?x=1", "bob", "s3cret", null);
        } finally {
            audit.detachAppender(appender);
        }
        assertThat(appender.list).hasSize(1);
        var line = appender.list.getFirst().getFormattedMessage();
        assertThat(line).contains("host=localhost", "classification=host_loopback", "outcome=ACCESSIBLE");
        assertThat(line).doesNotContain("s3cret", "private-repo", "bob:");
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