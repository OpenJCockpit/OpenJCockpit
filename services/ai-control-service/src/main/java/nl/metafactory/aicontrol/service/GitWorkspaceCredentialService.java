package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Hands the git credentials of an active git-workspace job to the git MCP server, so that the job's
 * workspace (key {@code job-<uuid>}) can clone, pull and push without any credential ever being a
 * tool argument. Only while the job is running, and only for the job's own project repository.
 * Secrets are decrypted here and never logged.
 */
@Service
public class GitWorkspaceCredentialService {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspaceCredentialService.class);
    private static final Pattern JOB_WORKSPACE_KEY = Pattern.compile(
            "^job-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");
    static final Set<GitWorkspaceJobStatus> FINISHED = Set.of(
            GitWorkspaceJobStatus.COMPLETED, GitWorkspaceJobStatus.FAILED, GitWorkspaceJobStatus.NO_CHANGES);

    /** {@code username}/{@code token} are both null for a repository without credentials (anonymous). */
    public record WorkspaceCredentials(String username, String token) {
        @Override
        public String toString() {
            return "WorkspaceCredentials[username=" + username + ", token=***]";
        }
    }

    private final GitWorkspaceJobRepository jobRepo;
    private final ProjectRepository projectRepo;
    private final ProjectGitResolver projectGit;
    private final CredentialEncryptionService encryption;

    public GitWorkspaceCredentialService(GitWorkspaceJobRepository jobRepo, ProjectRepository projectRepo,
                                         ProjectGitResolver projectGit, CredentialEncryptionService encryption) {
        this.jobRepo = jobRepo;
        this.projectRepo = projectRepo;
        this.projectGit = projectGit;
        this.encryption = encryption;
    }

    /** The workspace key of a git-workspace job, as used in the git MCP server. */
    public static String workspaceKey(UUID jobId) {
        return "job-" + jobId;
    }

    /** Empty when the key is unknown, the job is finished or the repository is not the job's project repository. */
    @Transactional(readOnly = true)
    public Optional<WorkspaceCredentials> credentialsFor(String workspaceKey, String repositoryUrl) {
        var matcher = workspaceKey == null ? null : JOB_WORKSPACE_KEY.matcher(workspaceKey);
        if (matcher == null || !matcher.matches() || repositoryUrl == null || repositoryUrl.isBlank()) {
            return Optional.empty();
        }
        Optional<GitWorkspaceJob> job = jobRepo.findById(UUID.fromString(matcher.group(1)));
        if (job.isEmpty() || job.get().getStatus() == null || FINISHED.contains(job.get().getStatus())) {
            log.warn("git.workspace.credentials.denied workspace={} reason=job-not-active", workspaceKey);
            return Optional.empty();
        }
        var project = projectRepo.findById(job.get().getProjectId());
        if (project.isEmpty() || !repositoryUrl.equals(project.get().getGitUrl())) {
            log.warn("git.workspace.credentials.denied workspace={} reason=repository-mismatch", workspaceKey);
            return Optional.empty();
        }
        ProjectGitCredential credential = projectGit.loadCredential(project.get().getId());
        log.info("git.workspace.credentials.served workspace={} project={}", workspaceKey, project.get().getId());
        if (credential == null || credential.getCredentialType() == GitCredentialType.NONE
                || credential.getEncryptedSecret() == null) {
            return Optional.of(new WorkspaceCredentials(null, null));
        }
        String username = credential.getUsername() != null ? credential.getUsername() : "token";
        return Optional.of(new WorkspaceCredentials(username, encryption.decrypt(credential.getEncryptedSecret())));
    }
}
