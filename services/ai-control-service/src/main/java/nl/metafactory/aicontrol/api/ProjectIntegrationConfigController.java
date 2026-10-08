package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.ProjectIntegrationConfigApi;
import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;
import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfigDto;
import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfigRequest;
import nl.metafactory.aicontrol.model.ProjectHermesConfig;
import nl.metafactory.aicontrol.model.ProjectHermesConfigDto;
import nl.metafactory.aicontrol.model.ProjectHermesConfigRequest;
import nl.metafactory.aicontrol.model.ProjectJiraConfig;
import nl.metafactory.aicontrol.model.ProjectJiraConfigDto;
import nl.metafactory.aicontrol.model.ProjectJiraConfigRequest;
import nl.metafactory.aicontrol.repository.ProjectDocumentFolderConfigRepository;
import nl.metafactory.aicontrol.repository.ProjectHermesConfigRepository;
import nl.metafactory.aicontrol.repository.ProjectJiraConfigRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ProjectIntegrationConfigController implements ProjectIntegrationConfigApi {

    private final ProjectService projectService;
    private final ProjectHermesConfigRepository hermesRepository;
    private final ProjectJiraConfigRepository jiraRepository;
    private final ProjectDocumentFolderConfigRepository documentFolderRepository;
    private final CredentialEncryptionService encryptionService;

    public ProjectIntegrationConfigController(ProjectService projectService,
                                               ProjectHermesConfigRepository hermesRepository,
                                               ProjectJiraConfigRepository jiraRepository,
                                               ProjectDocumentFolderConfigRepository documentFolderRepository,
                                               CredentialEncryptionService encryptionService) {
        this.projectService = projectService;
        this.hermesRepository = hermesRepository;
        this.jiraRepository = jiraRepository;
        this.documentFolderRepository = documentFolderRepository;
        this.encryptionService = encryptionService;
    }

    // ── Hermes ───────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<ProjectHermesConfigDto> getHermesConfig(UUID id) {
        projectService.findById(id);
        var config = hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .orElse(new ProjectHermesConfig());
        return ResponseEntity.ok(toHermesDto(id, config));
    }

    @Override
    public ResponseEntity<ProjectHermesConfigDto> saveHermesConfig(UUID id, ProjectHermesConfigRequest req) {
        projectService.findById(id);
        var existing = hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1);
        var config = existing.orElseGet(ProjectHermesConfig::new);
        config.setProjectId(id);
        config.setEnabled((short) (req.enabled() ? 1 : 0));
        config.setEndpointUrl(req.endpointUrl());
        config.setSignalType(req.signalType());
        config.setWorkflowId(req.workflowId());
        applyAuthToken(req.authToken(), existing.isPresent(), config::setEncryptedAuthToken);
        config.setActive((short) 1);
        hermesRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toHermesDto(id, config));
    }

    @Override
    public ResponseEntity<Void> deleteHermesConfig(UUID id) {
        projectService.findById(id);
        hermesRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .ifPresent(c -> { c.setActive((short) 0); hermesRepository.save(c); });
        return ResponseEntity.noContent().build();
    }

    // ── Jira ─────────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<ProjectJiraConfigDto> getJiraConfig(UUID id) {
        projectService.findById(id);
        var config = jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .orElse(new ProjectJiraConfig());
        return ResponseEntity.ok(toJiraDto(id, config));
    }

    @Override
    public ResponseEntity<ProjectJiraConfigDto> saveJiraConfig(UUID id, ProjectJiraConfigRequest req) {
        projectService.findById(id);
        var existing = jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1);
        var config = existing.orElseGet(ProjectJiraConfig::new);
        config.setProjectId(id);
        config.setEnabled((short) (req.enabled() ? 1 : 0));
        config.setBaseUrl(req.baseUrl());
        config.setProjectKey(req.projectKey());
        config.setIssueTypeMapping(req.issueTypeMapping());
        config.setWorkflowId(req.workflowId());
        applyAuthToken(req.authToken(), existing.isPresent(), config::setEncryptedAuthToken);
        config.setActive((short) 1);
        jiraRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toJiraDto(id, config));
    }

    @Override
    public ResponseEntity<Void> deleteJiraConfig(UUID id) {
        projectService.findById(id);
        jiraRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .ifPresent(c -> { c.setActive((short) 0); jiraRepository.save(c); });
        return ResponseEntity.noContent().build();
    }

    // ── Document folder ──────────────────────────────────────────────────────

    @Override
    public ResponseEntity<ProjectDocumentFolderConfigDto> getDocumentFolderConfig(UUID id) {
        var project = projectService.findById(id);
        var config = documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .orElse(new ProjectDocumentFolderConfig());
        return ResponseEntity.ok(toDocumentFolderDto(id, project.name(), config));
    }

    @Override
    public ResponseEntity<ProjectDocumentFolderConfigDto> saveDocumentFolderConfig(UUID id,
                                                                    ProjectDocumentFolderConfigRequest req) {
        var project = projectService.findById(id);
        var config = documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .orElseGet(ProjectDocumentFolderConfig::new);
        config.setProjectId(id);
        config.setFolderPath(req.folderPath());
        config.setFileTriggerEnabled((short) (req.fileTriggerEnabled() ? 1 : 0));
        config.setAllowedDocumentTypes(req.allowedDocumentTypes());
        config.setWorkflowId(req.workflowId());
        config.setActive((short) 1);
        documentFolderRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDocumentFolderDto(id, project.name(), config));
    }

    @Override
    public ResponseEntity<Void> deleteDocumentFolderConfig(UUID id) {
        projectService.findById(id);
        documentFolderRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .ifPresent(c -> { c.setActive((short) 0); documentFolderRepository.save(c); });
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyAuthToken(String rawToken, boolean hadExisting, java.util.function.Consumer<String> setter) {
        if (rawToken == null) {
            if (!hadExisting) {
                setter.accept(null);
            }
        } else if (rawToken.isEmpty()) {
            setter.accept(null);
        } else {
            setter.accept(encryptionService.encrypt(rawToken));
        }
    }

    private ProjectHermesConfigDto toHermesDto(UUID projectId, ProjectHermesConfig config) {
        return new ProjectHermesConfigDto(projectId, config.getEnabled() == 1, config.getEndpointUrl(),
                config.getSignalType(), config.getWorkflowId(), config.getEncryptedAuthToken() != null);
    }

    private ProjectJiraConfigDto toJiraDto(UUID projectId, ProjectJiraConfig config) {
        return new ProjectJiraConfigDto(projectId, config.getEnabled() == 1, config.getBaseUrl(),
                config.getProjectKey(), config.getIssueTypeMapping(), config.getWorkflowId(),
                config.getEncryptedAuthToken() != null);
    }

    private ProjectDocumentFolderConfigDto toDocumentFolderDto(UUID projectId, String projectName,
                                                                ProjectDocumentFolderConfig config) {
        return new ProjectDocumentFolderConfigDto(projectId, projectName, projectName, config.getFolderPath(),
                config.getFileTriggerEnabled() == 1, config.getAllowedDocumentTypes(), config.getWorkflowId());
    }
}
