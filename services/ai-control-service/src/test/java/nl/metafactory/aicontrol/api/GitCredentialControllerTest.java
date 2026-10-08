package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.ProjectGitCredentialRequest;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GitCredentialController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class GitCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ProjectGitCredentialRepository credentialRepository;

    @MockitoBean
    private CredentialEncryptionService encryptionService;

    @MockitoBean
    private GitConnectivityService connectivityService;

    @Test
    void saveCredentialsReturns201ForNewCredentialWhenGitCheckPasses() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("my-token")).thenReturn("encrypted-cipher");
        when(connectivityService.checkGitAccess(anyString(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials"));
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", "my-token", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void saveCredentialsReturns201WhenUpdatingExistingCredential() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectGitCredential();
        existing.setProjectId(id);
        existing.setEncryptedSecret("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(existing));
        when(connectivityService.checkGitAccess(anyString(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials"));
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // null secret means "do not change" — the existing encrypted secret is kept and re-verified
        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", null, null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void saveCredentialsReturns201ForNewCredentialWithNullSecret() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // credentialType NONE never needs a secret or a git check
        var req = new ProjectGitCredentialRequest(GitCredentialType.NONE, "user", null, null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void saveCredentialsReturns201WhenClearingSecret() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectGitCredential();
        existing.setProjectId(id);
        existing.setEncryptedSecret("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(existing));
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // empty string means "clear secret"; credentialType null defaults to NONE so no check runs
        var req = new ProjectGitCredentialRequest(null, null, "", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void saveCredentialsReturns400WhenSecretMissingForNonNoneType() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());

        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", null, null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A secret/token is required for credential type GITHUB_PAT"));
    }

    @Test
    void saveCredentialsReturns400WhenProjectHasNoGitUrl() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dtoWithGitUrl(id, null));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-cipher");

        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", "my-token", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Project has no Git URL configured; cannot verify credentials"));
    }

    @Test
    void saveCredentialsReturns422WhenGitCheckFails() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("bad-token")).thenReturn("encrypted-cipher");
        when(connectivityService.checkGitAccess(anyString(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Authentication failed"));

        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", "bad-token", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Git credentials could not be verified: Authentication failed"));
    }

    @Test
    void saveCredentialsDoesNotPersistWhenGitCheckFails() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("bad-token")).thenReturn("encrypted-cipher");
        when(connectivityService.checkGitAccess(anyString(), any()))
                .thenReturn(new GitConnectivityService.CheckResult(GitStatus.NOT_ACCESSIBLE, "Authentication failed"));

        var req = new ProjectGitCredentialRequest(GitCredentialType.GITHUB_PAT, "user", "bad-token", null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());

        org.mockito.Mockito.verify(credentialRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void saveCredentialsReturns404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenThrow(new ResponseStatusException(NOT_FOUND));

        var req = new ProjectGitCredentialRequest(GitCredentialType.NONE, null, null, null);
        mockMvc.perform(post("/api/projects/{id}/git-credentials", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCredentialsReturnsExistingMetadataWithoutSecret() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectGitCredential();
        existing.setProjectId(id);
        existing.setCredentialType(GitCredentialType.GITHUB_PAT);
        existing.setUsername("ci-bot");
        existing.setGithubApiUrl("https://github.example.com/api/v3");
        existing.setEncryptedSecret("cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(existing));

        mockMvc.perform(get("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialType").value("GITHUB_PAT"))
                .andExpect(jsonPath("$.username").value("ci-bot"))
                .andExpect(jsonPath("$.githubApiUrl").value("https://github.example.com/api/v3"))
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void getCredentialsReturnsNoneWhenNothingSaved() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialType").value("NONE"))
                .andExpect(jsonPath("$.hasSecret").value(false));
    }

    @Test
    void getCredentialsReturns404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCredentialsReturns204() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectGitCredential();
        existing.setProjectId(id);
        existing.setActive((short) 1);
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.of(existing));
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCredentialsReturns204WhenNoCredentialsExist() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCredentialsReturns404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(eq(id))).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(delete("/api/projects/{id}/git-credentials", id))
                .andExpect(status().isNotFound());
    }

    private ProjectDto dto(UUID id) {
        return dtoWithGitUrl(id, "https://github.com/org/repo");
    }

    private ProjectDto dtoWithGitUrl(UUID id, String gitUrl) {
        return new ProjectDto(id, "Test", null, gitUrl, null, "main", null, null,
                (short) 1, (short) 0, GitStatus.UNKNOWN,
                null, null, false, Instant.now(), Instant.now());
    }
}
