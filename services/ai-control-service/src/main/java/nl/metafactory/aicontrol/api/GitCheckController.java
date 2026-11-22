package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.GitCheckApi;
import nl.metafactory.aicontrol.model.GitConnectionCheckRequest;
import nl.metafactory.aicontrol.model.GitConnectionCheckResult;
import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.ProjectGitStatusDto;
import nl.metafactory.aicontrol.service.GitCheckRateLimiter;
import nl.metafactory.aicontrol.service.GitConnectivityService;
import nl.metafactory.aicontrol.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class GitCheckController implements GitCheckApi {

    private final GitConnectivityService connectivityService;
    private final ProjectService projectService;
    private final GitCheckRateLimiter rateLimiter;

    public GitCheckController(GitConnectivityService connectivityService, ProjectService projectService,
                              GitCheckRateLimiter rateLimiter) {
        this.connectivityService = connectivityService;
        this.projectService = projectService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ResponseEntity<GitConnectionCheckResult> checkGitConnection(GitConnectionCheckRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        rateLimiter.acquire(auth != null ? auth.getName() : "unknown");
        var type = req.credentialType() != null ? req.credentialType() : GitCredentialType.NONE;
        boolean needsSecret = type != GitCredentialType.NONE;
        if (needsSecret && (req.secret() == null || req.secret().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A secret/token is required for credential type " + type);
        }
        try {
            var check = connectivityService.checkGitConnection(req.gitUrl(),
                    needsSecret ? req.username() : null, needsSecret ? req.secret() : null,
                    needsSecret ? req.githubApiUrl() : null);
            return ResponseEntity.ok(new GitConnectionCheckResult(check.result().status(),
                    check.result().message(), check.warnings().stream().toList()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public ResponseEntity<ProjectGitStatusDto> triggerGitCheck(UUID id) {
        connectivityService.checkProjectGitAccess(id);
        return ResponseEntity.ok(toStatusDto(projectService.findById(id)));
    }

    @Override
    public ResponseEntity<ProjectGitStatusDto> getGitStatus(UUID id) {
        return ResponseEntity.ok(toStatusDto(projectService.findById(id)));
    }

    private ProjectGitStatusDto toStatusDto(nl.metafactory.aicontrol.model.ProjectDto p) {
        return new ProjectGitStatusDto(p.id(), p.gitStatus(), p.gitStatusCheckedAt(), p.gitStatusMessage());
    }
}
