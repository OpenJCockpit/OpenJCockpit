package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.GitCredentialApi;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.ProjectGitCredentialDto;
import nl.metafactory.aicontrol.model.ProjectGitCredentialRequest;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class GitCredentialController implements GitCredentialApi {

    private final ProjectService projectService;
    private final ProjectGitCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final GitConnectivityService connectivityService;

    public GitCredentialController(ProjectService projectService,
                                   ProjectGitCredentialRepository credentialRepository,
                                   CredentialEncryptionService encryptionService,
                                   GitConnectivityService connectivityService) {
        this.projectService = projectService;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.connectivityService = connectivityService;
    }

    @Override
    public ResponseEntity<ProjectGitCredentialDto> getCredentials(UUID id) {
        projectService.findById(id); // validates project exists, throws 404 if not found
        var dto = credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .map(c -> new ProjectGitCredentialDto(c.getCredentialType(), c.getUsername(),
                        c.getGithubApiUrl(), c.getEncryptedSecret() != null))
                .orElseGet(() -> new ProjectGitCredentialDto(GitCredentialType.NONE, null, null, false));
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Object> saveCredentials(UUID id, ProjectGitCredentialRequest req) {
        var project = projectService.findById(id); // validates project exists, throws 404 if not found
        var existing = credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1);
        var credential = existing.orElseGet(ProjectGitCredential::new);
        credential.setProjectId(id);
        GitCredentialType type = req.credentialType() != null ? req.credentialType() : GitCredentialType.NONE;
        credential.setCredentialType(type);
        credential.setUsername(req.username());
        credential.setGithubApiUrl(req.githubApiUrl());
        if (req.secret() == null) {
            if (existing.isEmpty()) {
                credential.setEncryptedSecret(null);
            }
            // else: blank secret on an update means "keep the current secret"
        } else if (req.secret().isEmpty()) {
            credential.setEncryptedSecret(null);
        } else {
            credential.setEncryptedSecret(encryptionService.encrypt(req.secret()));
        }

        if (type != GitCredentialType.NONE) {
            if (credential.getEncryptedSecret() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "A secret/token is required for credential type " + type));
            }
            if (project.gitUrl() == null || project.gitUrl().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Project has no Git URL configured; cannot verify credentials"));
            }
            var checkResult = connectivityService.checkGitAccess(project.gitUrl(), credential);
            if (checkResult.status() != GitStatus.ACCESSIBLE) {
                return ResponseEntity.unprocessableEntity()
                        .body(Map.of("message", "Git credentials could not be verified: " + checkResult.message()));
            }
        }

        credential.setActive((short) 1);
        credentialRepository.save(credential);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> deleteCredentials(UUID id) {
        projectService.findById(id); // validates project exists
        credentialRepository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(id, (short) 1)
                .ifPresent(c -> {
                    c.setActive((short) 0);
                    credentialRepository.save(c);
                });
        return ResponseEntity.noContent().build();
    }
}
