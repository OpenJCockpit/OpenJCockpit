package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.ProjectSpecApi;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecFileSaveRequest;
import nl.metafactory.aicontrol.model.SpecInitResult;
import nl.metafactory.aicontrol.model.SpecInitStatus;
import nl.metafactory.aicontrol.service.GitWorkspaceException;
import nl.metafactory.aicontrol.service.ProjectSpecService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ProjectSpecController implements ProjectSpecApi {

    public record SpecErrorResponse(String code, String message) {}

    private final ProjectSpecService service;

    public ProjectSpecController(ProjectSpecService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<SpecFile>> listSpecFiles(UUID projectId) {
        return ResponseEntity.ok(service.listSpecFiles(projectId));
    }

    @Override
    public ResponseEntity<SpecInitStatus> specInitStatus(UUID projectId) {
        return ResponseEntity.ok(service.specInitStatus(projectId));
    }

    @Override
    public ResponseEntity<SpecInitResult> initSpecFolder(UUID projectId) {
        String username = currentUsername();
        return ResponseEntity.ok(service.initSpecFolder(projectId, username));
    }

    @Override
    public ResponseEntity<SpecInitResult> saveSpecFile(UUID projectId, SpecFileSaveRequest request) {
        String username = currentUsername();
        return ResponseEntity.ok(service.saveSpecFile(projectId, request.fileName(), request.content(), username));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("preferred_username")
                : "unknown";
    }

    @ExceptionHandler(GitWorkspaceException.class)
    public ResponseEntity<SpecErrorResponse> handleGitWorkspaceException(GitWorkspaceException e) {
        HttpStatus status = switch (e.getErrorCode()) {
            case NO_SELECTED_PROJECT, PROJECT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROJECT_INACTIVE, GIT_URL_MISSING, GIT_AUTH_FAILED, NO_CHANGES -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status)
                .body(new SpecErrorResponse(e.getErrorCode().name(), e.getMessage()));
    }
}
