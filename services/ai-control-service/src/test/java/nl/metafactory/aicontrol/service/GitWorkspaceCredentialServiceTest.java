package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitWorkspaceCredentialServiceTest {

    private static final String REPO = "https://github.com/org/repo.git";

    private GitWorkspaceJobRepository jobRepo;
    private ProjectGitCredentialRepository credRepo;
    private CredentialEncryptionService encryption;
    private GitWorkspaceCredentialService service;
    private GitWorkspaceJob job;
    private Project project;

    @BeforeEach
    void setUp() {
        jobRepo = mock(GitWorkspaceJobRepository.class);
        var projectRepo = mock(ProjectRepository.class);
        credRepo = mock(ProjectGitCredentialRepository.class);
        encryption = mock(CredentialEncryptionService.class);
        service = new GitWorkspaceCredentialService(jobRepo, projectRepo,
                new ProjectGitResolver(projectRepo, credRepo, new AgenticWorkflowProperties()), encryption);

        project = new Project();
        project.setId(UUID.randomUUID());
        project.setGitUrl(REPO);
        job = new GitWorkspaceJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(project.getId());
        job.setStatus(GitWorkspaceJobStatus.AGENTS_RUNNING);
        when(jobRepo.findById(job.getId())).thenReturn(Optional.of(job));
        when(projectRepo.findById(project.getId())).thenReturn(Optional.of(project));

        var credential = new ProjectGitCredential();
        credential.setCredentialType(GitCredentialType.HTTPS_TOKEN);
        credential.setUsername("bot");
        credential.setEncryptedSecret("enc");
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(any(), anyShort())).thenReturn(Optional.of(credential));
        when(encryption.decrypt("enc")).thenReturn("t0k");
    }

    private String key() {
        return GitWorkspaceCredentialService.workspaceKey(job.getId());
    }

    @Test
    void anActiveJobGetsItsProjectCredentialsForItsOwnRepository() {
        var credentials = service.credentialsFor(key(), REPO).orElseThrow();

        assertThat(credentials.username()).isEqualTo("bot");
        assertThat(credentials.token()).isEqualTo("t0k");
        assertThat(credentials.toString()).doesNotContain("t0k");
    }

    @Test
    void anotherRepositoryNeverReceivesTheJobsCredentials() {
        assertThat(service.credentialsFor(key(), "https://evil.example/x.git")).isEmpty();
        verify(encryption, never()).decrypt(any());
    }

    @ParameterizedTest
    @EnumSource(value = GitWorkspaceJobStatus.class, names = {"COMPLETED", "FAILED", "NO_CHANGES"})
    void aFinishedJobHasNoCredentialsAnyMore(GitWorkspaceJobStatus finished) {
        job.setStatus(finished);

        assertThat(service.credentialsFor(key(), REPO)).isEmpty();
        verify(encryption, never()).decrypt(any());
    }

    @Test
    void unknownOrMalformedKeysAreRejected() {
        assertThat(service.credentialsFor("job-" + UUID.randomUUID(), REPO)).isEmpty();
        assertThat(service.credentialsFor("run-1", REPO)).isEmpty();
        assertThat(service.credentialsFor(null, REPO)).isEmpty();
        assertThat(service.credentialsFor(key(), " ")).isEmpty();
    }

    @Test
    void aRepositoryWithoutCredentialsIsAnonymous() {
        when(credRepo.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(any(), anyShort())).thenReturn(Optional.empty());

        var credentials = service.credentialsFor(key(), REPO).orElseThrow();

        assertThat(credentials.username()).isNull();
        assertThat(credentials.token()).isNull();
    }
}
