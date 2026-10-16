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
 * kept so the workflow can run where the repository is cloned (the built-in
 * git publication of the agent service was removed). Secrets only leave the
 * ai-control-service towards the agent service — never towards the
 * browser.
 */
@Service
public class WorkflowStartEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStartEnrichmentService.class);

    private final ProjectRepository projectRepository;
    private final ProjectGitCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryption;
    private final SpecContentSource specContents;

    /** Reads the content of a spec of a project's repository ({@code specs/} folder) by its file name. */
    @FunctionalInterface
    public interface SpecContentSource {
        Optional<String> read(UUID projectId, String fileName);
    }

    WorkflowStartEnrichmentService(ProjectRepository projectRepository,
                                   ProjectGitCredentialRepository credentialRepository,
                                   CredentialEncryptionService encryption) {
        this(projectRepository, credentialRepository, encryption, (projectId, fileName) -> Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowStartEnrichmentService(ProjectRepository projectRepository,
                                          ProjectGitCredentialRepository credentialRepository,
                                          CredentialEncryptionService encryption,
                                          ProjectSpecService projectSpecs) {
        this(projectRepository, credentialRepository, encryption, projectSpecs::readSpecContent);
    }

    WorkflowStartEnrichmentService(ProjectRepository projectRepository,
                                   ProjectGitCredentialRepository credentialRepository,
                                   CredentialEncryptionService encryption,
                                   SpecContentSource specContents) {
        this.projectRepository = projectRepository;
        this.credentialRepository = credentialRepository;
        this.encryption = encryption;
        this.specContents = specContents;
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

        return new WorkflowStartInputDto(input.prompt(), specPayload(projectId, input.specFile()), repositoryUrl,
                input.projectId(), username, token, baseBranch);
    }

    /**
     * The dashboard sends the selected spec's file name. The agents get the spec itself as their input,
     * read here from the project repository's {@code specs/} folder, so they need neither a git clone nor
     * git tools to know what to work on. A name that is not a spec of the project is passed on unchanged.
     */
    String specPayload(UUID projectId, String specFile) {
        if (specFile == null || specFile.isBlank() || specFile.contains("\n")) {
            return specFile;
        }
        String name = specFile.trim();
        try {
            return specContents.read(projectId, name)
                    .map(content -> "# Spec: specs/" + name + "\n\n" + content)
                    .orElseGet(() -> {
                        log.warn("Selected spec {} not found in the specs folder of project {}; passing its name", name, projectId);
                        return specFile;
                    });
        } catch (RuntimeException e) {
            log.warn("Spec {} of project {} could not be read ({}); passing its name", name, projectId,
                    e.getClass().getSimpleName());
            return specFile;
        }
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
