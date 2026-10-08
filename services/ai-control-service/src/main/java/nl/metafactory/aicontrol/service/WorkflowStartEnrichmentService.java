package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Enriches the workflow start input with project data before it is sent to the
 * embabel-agent-service: the project's repository URL (if the client did not
 * provide one) and the project's active git credentials (decrypted),
 * so that the SpecGitPublisher can clone and push. Secrets only leave the
 * ai-control-service towards the agent service — never towards the
 * browser.
 */
@Service
public class WorkflowStartEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStartEnrichmentService.class);

    private final ProjectRepository projectRepository;
    private final ProjectGitCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryption;

    public WorkflowStartEnrichmentService(ProjectRepository projectRepository,
                                          ProjectGitCredentialRepository credentialRepository,
                                          CredentialEncryptionService encryption) {
        this.projectRepository = projectRepository;
        this.credentialRepository = credentialRepository;
        this.encryption = encryption;
    }

    public WorkflowStartInputDto enrich(WorkflowStartInputDto input) {
        UUID projectId = parseProjectId(input.projectId());
        if (projectId == null) {
            return input;
        }

        // Q1 (MADP-54): the project always wins for baseBranch, exactly as it already does for
        // credentials — so the project is loaded unconditionally, with exactly one findById per start.
        Optional<Project> project = projectRepository.findById(projectId);

        String repositoryUrl = input.repositoryUrl();
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            repositoryUrl = project.map(Project::getGitUrl).orElse(repositoryUrl);
        }

        // A caller-supplied baseBranch only survives when no project context is available; when the
        // project resolves it is overwritten with the project's defaultBranch — including to null
        // when that branch is blank (Q1 read literally; see 04-release-note.md).
        String baseBranch = trimToNull(input.baseBranch());
        if (project.isPresent()) {
            baseBranch = trimToNull(project.get().getDefaultBranch());
        }

        String username = input.gitUsername();
        String token = input.gitToken();
        ProjectGitCredential credential = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);
        if (credential != null && credential.getCredentialType() != GitCredentialType.NONE) {
            username = credential.getUsername() != null ? credential.getUsername() : "token";
            token = credential.getEncryptedSecret() != null
                    ? encryption.decrypt(credential.getEncryptedSecret()) : "";
        }

        return new WorkflowStartInputDto(input.prompt(), input.specFile(), repositoryUrl,
                input.projectId(), username, token, baseBranch);
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private UUID parseProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid project id at workflow start: {}", projectId);
            return null;
        }
    }
}
