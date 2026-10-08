package nl.metafactory.aicontrol.api;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;
import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfigRequest;
import nl.metafactory.aicontrol.model.ProjectDto;
import nl.metafactory.aicontrol.model.ProjectHermesConfig;
import nl.metafactory.aicontrol.model.ProjectHermesConfigRequest;
import nl.metafactory.aicontrol.model.ProjectJiraConfig;
import nl.metafactory.aicontrol.model.ProjectJiraConfigRequest;
import nl.metafactory.aicontrol.repository.ProjectDocumentFolderConfigRepository;
import nl.metafactory.aicontrol.repository.ProjectHermesConfigRepository;
import nl.metafactory.aicontrol.repository.ProjectJiraConfigRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ProjectIntegrationConfigController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class}
)
class ProjectIntegrationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private ProjectHermesConfigRepository hermesRepository;
    @MockitoBean private ProjectJiraConfigRepository jiraRepository;
    @MockitoBean private ProjectDocumentFolderConfigRepository documentFolderRepository;
    @MockitoBean private CredentialEncryptionService encryptionService;

    private ProjectDto dto(UUID id) {
        return new ProjectDto(id, "Noordzee Logistics", null, null, null, "main", null, null,
                (short) 1, (short) 0, nl.metafactory.aicontrol.model.GitStatus.UNKNOWN,
                null, null, false, Instant.now(), Instant.now());
    }

    // ── Hermes ───────────────────────────────────────────────────────────────

    @Test
    void getHermesConfigReturnsDefaultsWhenNoneSaved() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{id}/hermes-config", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void saveHermesConfigCreatesNewConfigAndEncryptsToken() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        when(encryptionService.encrypt("hermes-token")).thenReturn("cipher");
        when(hermesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectHermesConfigRequest(true, "https://hermes.example.com", "hermes-token", "issue.created", "wf-1");
        mockMvc.perform(post("/api/projects/{id}/hermes-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasAuthToken").value(true))
                .andExpect(jsonPath("$.workflowId").value("wf-1"));
    }

    @Test
    void saveHermesConfigCreatesNewConfigWithoutTokenWhenNullProvided() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        when(hermesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectHermesConfigRequest(false, "https://hermes.example.com", null, "issue.created", null);
        mockMvc.perform(post("/api/projects/{id}/hermes-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void saveHermesConfigKeepsExistingTokenWhenNullProvided() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectHermesConfig();
        existing.setProjectId(id);
        existing.setEncryptedAuthToken("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(hermesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectHermesConfigRequest(true, "https://hermes.example.com", null, "issue.created", "wf-1");
        mockMvc.perform(post("/api/projects/{id}/hermes-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAuthToken").value(true));
    }

    @Test
    void saveHermesConfigClearsTokenWhenEmptyStringProvided() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectHermesConfig();
        existing.setProjectId(id);
        existing.setEncryptedAuthToken("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(hermesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectHermesConfigRequest(false, null, "", null, null);
        mockMvc.perform(post("/api/projects/{id}/hermes-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void deleteHermesConfigReturns204() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectHermesConfig();
        existing.setActive((short) 1);
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(hermesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/projects/{id}/hermes-config", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteHermesConfigReturns204WhenNoneExist() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/projects/{id}/hermes-config", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void hermesEndpointsReturn404WhenProjectNotFound() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/projects/{id}/hermes-config", id)).andExpect(status().isNotFound());
    }

    // ── Jira ─────────────────────────────────────────────────────────────────

    @Test
    void getJiraConfigReturnsDefaultsWhenNoneSaved() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{id}/jira-config", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void saveJiraConfigCreatesNewConfigAndEncryptsToken() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        when(encryptionService.encrypt("jira-token")).thenReturn("cipher");
        when(jiraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectJiraConfigRequest(true, "https://noordzee.atlassian.net", "NL", "jira-token", "Bug=bugfix-workflow", "wf-1");
        mockMvc.perform(post("/api/projects/{id}/jira-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("NL"))
                .andExpect(jsonPath("$.hasAuthToken").value(true));
    }

    @Test
    void saveJiraConfigKeepsExistingTokenWhenNullProvided() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectJiraConfig();
        existing.setProjectId(id);
        existing.setEncryptedAuthToken("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(jiraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectJiraConfigRequest(true, "https://noordzee.atlassian.net", "NL", null, "Bug=bugfix-workflow", "wf-1");
        mockMvc.perform(post("/api/projects/{id}/jira-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAuthToken").value(true));
    }

    @Test
    void saveJiraConfigClearsTokenWhenEmptyStringProvided() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectJiraConfig();
        existing.setProjectId(id);
        existing.setEncryptedAuthToken("old-cipher");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(jiraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectJiraConfigRequest(false, null, null, "", null, null);
        mockMvc.perform(post("/api/projects/{id}/jira-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAuthToken").value(false));
    }

    @Test
    void deleteJiraConfigReturns204() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectJiraConfig();
        existing.setActive((short) 1);
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(jiraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/projects/{id}/jira-config", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteJiraConfigReturns204WhenNoneExist() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/projects/{id}/jira-config", id))
                .andExpect(status().isNoContent());
    }

    // ── Document folder ──────────────────────────────────────────────────────

    @Test
    void getDocumentFolderConfigReturnsProjectNameAsFolderName() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{id}/document-folder-config", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderName").value("Noordzee Logistics"))
                .andExpect(jsonPath("$.projectName").value("Noordzee Logistics"));
    }

    @Test
    void saveDocumentFolderConfigCreatesNewConfig() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());
        when(documentFolderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectDocumentFolderConfigRequest("/documents", true, "pdf,docx", "wf-1");
        mockMvc.perform(post("/api/projects/{id}/document-folder-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folderPath").value("/documents"))
                .andExpect(jsonPath("$.fileTriggerEnabled").value(true))
                .andExpect(jsonPath("$.folderName").value("Noordzee Logistics"));
    }

    @Test
    void saveDocumentFolderConfigUpdatesExistingConfig() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectDocumentFolderConfig();
        existing.setProjectId(id);
        existing.setFolderPath("/old-path");
        when(projectService.findById(id)).thenReturn(dto(id));
        when(documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(documentFolderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProjectDocumentFolderConfigRequest("/new-path", false, "pdf", "wf-2");
        mockMvc.perform(post("/api/projects/{id}/document-folder-config", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folderPath").value("/new-path"));
    }

    @Test
    void deleteDocumentFolderConfigReturns204() throws Exception {
        var id = UUID.randomUUID();
        var existing = new ProjectDocumentFolderConfig();
        existing.setActive((short) 1);
        when(projectService.findById(id)).thenReturn(dto(id));
        when(documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.of(existing));
        when(documentFolderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/projects/{id}/document-folder-config", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDocumentFolderConfigReturns204WhenNoneExist() throws Exception {
        var id = UUID.randomUUID();
        when(projectService.findById(id)).thenReturn(dto(id));
        when(documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/projects/{id}/document-folder-config", id))
                .andExpect(status().isNoContent());
    }
}
