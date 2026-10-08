package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowStartEnrichmentServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ProjectRepository projectRepository;
    private ProjectGitCredentialRepository credentialRepository;
    private CredentialEncryptionService encryption;
    private WorkflowStartEnrichmentService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        credentialRepository = mock(ProjectGitCredentialRepository.class);
        encryption = mock(CredentialEncryptionService.class);
        service = new WorkflowStartEnrichmentService(projectRepository, credentialRepository, encryption);
    }

    private WorkflowStartInputDto input(String repositoryUrl, String projectId) {
        return new WorkflowStartInputDto("Create a spec", null, repositoryUrl, projectId, null, null, null);
    }

    private WorkflowStartInputDto input(String repositoryUrl, String projectId, String baseBranch) {
        return new WorkflowStartInputDto("Create a spec", null, repositoryUrl, projectId, null, null, baseBranch);
    }

    private Project projectWithBranch(String defaultBranch) {
        var project = new Project();
        project.setGitUrl("https://github.com/org/from-project.git");
        project.setDefaultBranch(defaultBranch);
        return project;
    }

    private ProjectGitCredential credential(GitCredentialType type, String username, String encryptedSecret) {
        var cred = new ProjectGitCredential();
        cred.setProjectId(PROJECT_ID);
        cred.setCredentialType(type);
        cred.setUsername(username);
        cred.setEncryptedSecret(encryptedSecret);
        return cred;
    }

    @Test
    void enrichLeavesInputUntouchedWithoutProjectId() {
        var withoutId = input("https://github.com/org/repo.git", null);
        var blankId = input("https://github.com/org/repo.git", "  ");

        assertThat(service.enrich(withoutId)).isSameAs(withoutId);
        assertThat(service.enrich(blankId)).isSameAs(blankId);
        verifyNoInteractions(projectRepository, credentialRepository, encryption);
    }

    @Test
    void enrichLeavesInputUntouchedForInvalidProjectId() {
        var invalid = input("https://github.com/org/repo.git", "not-a-uuid");

        assertThat(service.enrich(invalid)).isSameAs(invalid);
        verifyNoInteractions(projectRepository, credentialRepository, encryption);
    }

    @Test
    void enrichAddsDecryptedProjectCredentials() {
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.GITHUB_PAT, "bot", "enc-secret")));
        when(encryption.decrypt("enc-secret")).thenReturn("plain-secret");

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString()));

        assertThat(result.gitUsername()).isEqualTo("bot");
        assertThat(result.gitToken()).isEqualTo("plain-secret");
        assertThat(result.repositoryUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(result.prompt()).isEqualTo("Create a spec");
    }

    @Test
    void enrichDefaultsUsernameToTokenAndSecretToEmpty() {
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.HTTPS_TOKEN, null, null)));

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString()));

        assertThat(result.gitUsername()).isEqualTo("token");
        assertThat(result.gitToken()).isEmpty();
        verifyNoInteractions(encryption);
    }

    @Test
    void enrichKeepsInputCredentialsWhenProjectHasNoneOrTypeNone() {
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());
        var withoutCredential = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString()));
        assertThat(withoutCredential.gitUsername()).isNull();
        assertThat(withoutCredential.gitToken()).isNull();

        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.of(credential(GitCredentialType.NONE, "user", "enc")));
        var noneType = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString()));
        assertThat(noneType.gitUsername()).isNull();
        assertThat(noneType.gitToken()).isNull();
        verifyNoInteractions(encryption);
    }

    @Test
    void enrichFillsRepositoryUrlFromProjectWhenMissing() {
        var project = new Project();
        project.setGitUrl("https://github.com/org/from-project.git");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var missingUrl = service.enrich(input(null, PROJECT_ID.toString()));
        var blankUrl = service.enrich(input("  ", PROJECT_ID.toString()));

        assertThat(missingUrl.repositoryUrl()).isEqualTo("https://github.com/org/from-project.git");
        assertThat(blankUrl.repositoryUrl()).isEqualTo("https://github.com/org/from-project.git");
    }

    @Test
    void enrichKeepsMissingRepositoryUrlWhenProjectUnknown() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(input(null, PROJECT_ID.toString()));

        assertThat(result.repositoryUrl()).isNull();
    }

    // ── MADP-54 AC-07 : baseBranch resolution matrix (Q1 — the project always wins) ──

    @Test
    void enrichResolvesBaseBranchFromProjectDefaultBranch() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch("develop")));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), null));

        assertThat(result.baseBranch()).isEqualTo("develop");
    }

    @Test
    void enrichResolvesBaseBranchToNullWhenProjectDefaultBranchIsBlankOrNull() {
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        for (String blank : new String[] {null, "", "   "}) {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch(blank)));
            var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), null));
            assertThat(result.baseBranch()).as("project branch = <%s>", blank).isNull();
        }
    }

    @Test
    void enrichIgnoresCallerBaseBranchWheneverTheProjectResolves() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch("develop")));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), "feature/from-caller"));

        assertThat(result.baseBranch()).isEqualTo("develop");
    }

    @Test
    void enrichOverwritesCallerBaseBranchToNullWhenTheProjectBranchIsBlank() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch("   ")));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), "feature/from-caller"));

        assertThat(result.baseBranch()).isNull();
    }

    @Test
    void enrichTrimsTheProjectDefaultBranch() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch("  develop  ")));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), null));

        assertThat(result.baseBranch()).isEqualTo("develop");
    }

    @Test
    void enrichKeepsCallerBaseBranchWhenTheProjectIsAbsent() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        var result = service.enrich(
                input("https://github.com/org/repo.git", PROJECT_ID.toString(), "  feature/from-caller  "));

        assertThat(result.baseBranch()).isEqualTo("feature/from-caller");
    }

    @Test
    void enrichKeepsCallerBaseBranchAndTouchesNoRepositoryWithoutProjectContext() {
        var withCallerBranch = input("https://github.com/org/repo.git", null, "feature/from-caller");

        assertThat(service.enrich(withCallerBranch)).isSameAs(withCallerBranch);
        verifyNoInteractions(projectRepository, credentialRepository, encryption);
    }

    @Test
    void enrichLooksUpTheProjectExactlyOncePerStart() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(projectWithBranch("develop")));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(PROJECT_ID, (short) 1))
                .thenReturn(Optional.empty());

        service.enrich(input("https://github.com/org/repo.git", PROJECT_ID.toString(), "feature/from-caller"));

        verify(projectRepository, times(1)).findById(PROJECT_ID);
    }
}
