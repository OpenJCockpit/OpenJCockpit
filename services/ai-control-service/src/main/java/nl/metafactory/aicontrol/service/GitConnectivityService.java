package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitConnectivityService {

    private final ProjectRepository projectRepository;
    private final ProjectGitCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final int checkTimeoutSeconds;
    final JGitPort jGitPort;
    final GitHubPort gitHubPort;

    public GitConnectivityService(
            ProjectRepository projectRepository,
            ProjectGitCredentialRepository credentialRepository,
            CredentialEncryptionService encryptionService,
            @Value("${openjcockpit.git.check-timeout-seconds:10}") int checkTimeoutSeconds,
            JGitPort jGitPort,
            GitHubPort gitHubPort) {
        this.projectRepository = projectRepository;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.checkTimeoutSeconds = checkTimeoutSeconds;
        this.jGitPort = jGitPort;
        this.gitHubPort = gitHubPort;
    }

    public GitStatus checkProjectGitAccess(UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            return saveStatus(project, GitStatus.UNKNOWN, "No Git URL configured");
        }
        var credential = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(projectId, (short) 1)
                .orElse(null);
        var result = checkGitAccess(project.getGitUrl(), credential);
        return saveStatus(project, result.status(), result.message());
    }

    public record CheckResult(GitStatus status, String message) {}

    /**
     * Pure connectivity check against a (possibly not-yet-persisted) credential — used both for
     * the project's "Git check" action and to verify new/updated credentials actually work
     * before {@link nl.metafactory.aicontrol.api.GitCredentialController} persists them.
     */
    public CheckResult checkGitAccess(String gitUrl, ProjectGitCredential credential) {
        try {
            jGitPort.checkReachable(gitUrl, null, checkTimeoutSeconds);
            return new CheckResult(GitStatus.ACCESSIBLE, "Reachable without authentication");
        } catch (Exception anonEx) {
            if (credential == null || credential.getEncryptedSecret() == null) {
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, friendlyMessage(anonEx));
            }
            String secret;
            try {
                secret = encryptionService.decrypt(credential.getEncryptedSecret());
            } catch (Exception decryptEx) {
                return new CheckResult(GitStatus.CHECK_FAILED, "Credential decryption failed");
            }
            String username = credential.getUsername() != null ? credential.getUsername() : "token";
            var cp = new UsernamePasswordCredentialsProvider(username, secret);
            try {
                jGitPort.checkReachable(gitUrl, cp, checkTimeoutSeconds);
                return new CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials");
            } catch (Exception credEx) {
                if (isGitHubUrl(gitUrl)) {
                    return checkWithGitHubApi(gitUrl, credential, secret);
                }
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, friendlyMessage(credEx));
            }
        }
    }

    private CheckResult checkWithGitHubApi(String gitUrl, ProjectGitCredential credential, String token) {
        try {
            String[] ownerRepo = parseGitHubOwnerRepo(gitUrl);
            if (ownerRepo == null) {
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, "Cannot parse GitHub repository from URL");
            }
            String apiUrl = credential.getGithubApiUrl() != null
                    ? credential.getGithubApiUrl()
                    : "https://api.github.com";
            gitHubPort.checkRepository(ownerRepo[0], ownerRepo[1], apiUrl, token);
            return new CheckResult(GitStatus.ACCESSIBLE, "Reachable via GitHub API");
        } catch (Exception e) {
            return new CheckResult(GitStatus.CHECK_FAILED, "GitHub API check failed: " + friendlyMessage(e));
        }
    }

    private GitStatus saveStatus(Project project, GitStatus status, String message) {
        project.setGitStatus(status);
        project.setGitStatusCheckedAt(Instant.now());
        project.setGitStatusMessage(message);
        projectRepository.save(project);
        return status;
    }

    boolean isGitHubUrl(String gitUrl) {
        if (gitUrl == null) return false;
        return gitUrl.contains("github.com") || gitUrl.contains("github.");
    }

    String[] parseGitHubOwnerRepo(String gitUrl) {
        Pattern httpsPattern = Pattern.compile("https?://[^/]+/([^/]+)/([^/]+?)(?:\\.git)?$");
        Matcher httpsMatcher = httpsPattern.matcher(gitUrl);
        if (httpsMatcher.find()) {
            return new String[]{httpsMatcher.group(1), httpsMatcher.group(2)};
        }
        Pattern sshPattern = Pattern.compile("git@[^:]+:([^/]+)/([^/]+?)(?:\\.git)?$");
        Matcher sshMatcher = sshPattern.matcher(gitUrl);
        if (sshMatcher.find()) {
            return new String[]{sshMatcher.group(1), sshMatcher.group(2)};
        }
        return null;
    }

    static String friendlyMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return "Git check failed";
        String lower = msg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("time out")) return "Connection timed out";
        if (lower.contains("auth") || lower.contains("credential")) return "Authentication failed";
        if (lower.contains("not found") || lower.contains("404")) return "Repository not found";
        return "Git check failed: " + msg;
    }
}
