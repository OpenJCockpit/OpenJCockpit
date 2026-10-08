package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredentialRequest;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the "save git credentials" flow through the real Spring context: the
 * real {@link CredentialEncryptionService} and a real (H2, migrated by Flyway) database — not the
 * mocked repository/encryptor used by {@link GitCredentialControllerTest}. This is what actually
 * proves a saved credential (a) lands in the database and (b) is never persisted as plaintext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class GitCredentialPersistenceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectGitCredentialRepository credentialRepository;
    @Autowired private CredentialEncryptionService encryptionService;
    @Autowired private EntityManager entityManager;

    @MockitoBean private EmbabelAgentClient embabelAgentClient;

    // Stubbed out so the controller's pre-save git check never makes a real network call —
    // that check's own logic is covered separately by GitConnectivityServiceTest and
    // GitCredentialControllerTest; this class is only concerned with persistence/encryption.
    @MockitoBean private GitConnectivityService connectivityService;

    private java.util.UUID projectId;

    @BeforeEach
    void setUp() {
        when(embabelAgentClient.getAgentDefinitions()).thenReturn(List.of(
                new AgentDefinitionDto("req", "Requirement Agent", "Extract requirements",
                        "specification", 0, "SpecContent", "RequirementAnalysis")
        ));
        when(connectivityService.checkGitAccess(anyString(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials"));

        var project = new Project();
        project.setName("Git Credential Persistence Test Project");
        project.setGitUrl("https://github.com/org/repo");
        project.setActive((short) 1);
        projectId = projectRepository.saveAndFlush(project).getId();
    }

    @Test
    void savedSecretIsPersistedEncryptedNotAsPlaintext() throws Exception {
        var plaintextToken = "ghp_realSecretTokenValue1234567890";
        var request = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "ci-bot", plaintextToken, null);

        mockMvc.perform(post("/api/projects/{id}/git-credentials", projectId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        var persisted = credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1);
        assertThat(persisted).isPresent();
        var credential = persisted.get();

        assertThat(credential.getEncryptedSecret()).isNotNull();
        assertThat(credential.getEncryptedSecret()).isNotEqualTo(plaintextToken);
        assertThat(credential.getEncryptedSecret()).doesNotContain(plaintextToken);
        assertThat(encryptionService.decrypt(credential.getEncryptedSecret())).isEqualTo(plaintextToken);

        assertThat(credential.getCredentialType()).isEqualTo(GitCredentialType.GITHUB_PAT);
        assertThat(credential.getUsername()).isEqualTo("ci-bot");
        assertThat(credential.getActive()).isEqualTo((short) 1);
        assertThat(credential.getProjectId()).isEqualTo(projectId);
    }

    @Test
    void savingAgainForTheSameProjectUpdatesTheExistingRowInPlace() throws Exception {
        var firstRequest = new ProjectGitCredentialRequest(GitCredentialType.HTTPS_TOKEN, "user", "first-secret", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", projectId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        var secondRequest = new ProjectGitCredentialRequest(GitCredentialType.HTTPS_TOKEN, "user", "rotated-secret", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", projectId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        var all = credentialRepository.findAll().stream()
                .filter(c -> c.getProjectId().equals(projectId))
                .toList();
        assertThat(all).hasSize(1);
        assertThat(encryptionService.decrypt(all.get(0).getEncryptedSecret())).isEqualTo("rotated-secret");
    }

    @Test
    void unauthenticatedRequestCannotSaveCredentials() throws Exception {
        var request = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", "secret", null);

        mockMvc.perform(post("/api/projects/{id}/git-credentials", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        assertThat(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)).isEmpty();
    }
}
