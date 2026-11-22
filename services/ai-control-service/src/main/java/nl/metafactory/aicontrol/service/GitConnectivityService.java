package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitConnectivityService {

    private static final Logger AUDIT = LoggerFactory.getLogger("nl.metafactory.aicontrol.audit.GitCheck");
    static final String UNSUPPORTED_URL_MESSAGE = "Unsupported URL: use https, ssh or git";

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

    /** Result of {@link #checkGitConnection}: the check outcome plus non-blocking warnings about the host. */
    public record ConnectionCheck(CheckResult result, Set<String> warnings) {}

    /**
     * Pure connectivity check against a (possibly not-yet-persisted) credential — used both for
     * the project's "Git check" action and to verify new/updated credentials actually work
     * before {@link nl.metafactory.aicontrol.api.GitCredentialController} persists them.
     */
    public CheckResult checkGitAccess(String gitUrl, ProjectGitCredential credential) {
        GitHostClassifier.Target target;
        try {
            target = GitHostClassifier.classifyGitUrl(gitUrl);
        } catch (GitHostClassifier.RejectedUrlException e) {
            audit("rejected", "rejected", GitStatus.NOT_ACCESSIBLE);
            return new CheckResult(GitStatus.NOT_ACCESSIBLE, UNSUPPORTED_URL_MESSAGE);
        }
        boolean hasSecret = credential != null && credential.getEncryptedSecret() != null;
        var result = check(gitUrl, hasSecret ? credential.getUsername() : null,
                hasSecret ? () -> encryptionService.decrypt(credential.getEncryptedSecret()) : null,
                hasSecret ? credential.getGithubApiUrl() : null);
        audit(target.host(), target.classification(), result.status());
        return result;
    }

    /**
     * Connectivity check for a URL and a plain secret supplied by the caller, before any project exists.
     * Nothing is persisted and the secret is neither logged nor returned.
     *
     * @throws IllegalArgumentException when the Git URL or GitHub API URL uses an unsupported scheme
     */
    public ConnectionCheck checkGitConnection(String gitUrl, String username, String secret, String githubApiUrl) {
        GitHostClassifier.Target target;
        Set<String> warnings = new TreeSet<>();
        try {
            target = GitHostClassifier.classifyGitUrl(gitUrl);
            warnings.addAll(target.warnings());
            if (githubApiUrl != null && !githubApiUrl.isBlank()) {
                warnings.addAll(GitHostClassifier.classifyApiUrl(githubApiUrl).warnings());
            }
        } catch (GitHostClassifier.RejectedUrlException e) {
            audit("rejected", "rejected", null);
            throw new IllegalArgumentException(UNSUPPORTED_URL_MESSAGE, e);
        }
        boolean hasSecret = secret != null && !secret.isEmpty();
        var result = check(gitUrl, username, hasSecret ? () -> secret : null,
                hasSecret && githubApiUrl != null && !githubApiUrl.isBlank() ? githubApiUrl : null);
        audit(target.host(), String.join(",", warnings.isEmpty() ? Set.of("public") : warnings), result.status());
        return new ConnectionCheck(result, Collections.unmodifiableSet(warnings));
    }

    /** {@code secret} is null for an anonymous-only check; it is only read after the anonymous attempt fails. */
    private CheckResult check(String gitUrl, String username, Supplier<String> secret, String githubApiUrl) {
        try {
            jGitPort.checkReachable(gitUrl, null, checkTimeoutSeconds);
            return new CheckResult(GitStatus.ACCESSIBLE, "Reachable without authentication");
        } catch (Exception anonEx) {
            if (secret == null) {
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, friendlyMessage(anonEx));
            }
            String token;
            try {
                token = secret.get();
            } catch (Exception decryptEx) {
                return new CheckResult(GitStatus.CHECK_FAILED, "Credential decryption failed");
            }
            var cp = new UsernamePasswordCredentialsProvider(username != null ? username : "token", token);
            try {
                jGitPort.checkReachable(gitUrl, cp, checkTimeoutSeconds);
                return new CheckResult(GitStatus.ACCESSIBLE, "Reachable with credentials");
            } catch (Exception credEx) {
                if (isGitHubUrl(gitUrl)) {
                    return checkWithGitHubApi(gitUrl, githubApiUrl, token);
                }
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, friendlyMessage(credEx));
            }
        }
    }

    private CheckResult checkWithGitHubApi(String gitUrl, String githubApiUrl, String token) {
        try {
            String[] ownerRepo = parseGitHubOwnerRepo(gitUrl);
            if (ownerRepo == null) {
                return new CheckResult(GitStatus.NOT_ACCESSIBLE, "Cannot parse GitHub repository from URL");
            }
            String apiUrl = githubApiUrl != null ? githubApiUrl : "https://api.github.com";
            gitHubPort.checkRepository(ownerRepo[0], ownerRepo[1], apiUrl, token);
            return new CheckResult(GitStatus.ACCESSIBLE, "Reachable via GitHub API");
        } catch (Exception e) {
            return new CheckResult(GitStatus.CHECK_FAILED, "GitHub API check failed: " + friendlyMessage(e));
        }
    }

    /** One line per check: who, which host, what it resolved to, what came back. Never the secret or the full URL. */
    private static void audit(String host, String classification, GitStatus outcome) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        AUDIT.info("git-check user={} host={} classification={} outcome={}",
                auth != null ? auth.getName() : "unknown", host, classification,
                outcome != null ? outcome : "REJECTED");
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
        if (lower.contains("ssl") || lower.contains("tls") || lower.contains("certificate")) {
            return "TLS certificate error";
        }
        // Never echo the exception text: it can reveal what is listening on an internal address.
        return "Git check failed";
    }
}
