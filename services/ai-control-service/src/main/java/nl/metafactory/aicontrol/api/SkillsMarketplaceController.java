package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.SkillsMarketplaceApi;
import nl.metafactory.aicontrol.model.ApiErrorResponse;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionDto;
import nl.metafactory.aicontrol.model.SkillsMarketplaceConnectionRequest;
import nl.metafactory.aicontrol.service.SkillsMarketplaceConnectionService;
import nl.metafactory.aicontrol.service.SkillsMarketplaceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Thin controller for the global skills-marketplace-connections resource (ADR-1). All business
 * rules (name normalisation/uniqueness, secret retention, soft-delete) live in
 * {@link SkillsMarketplaceConnectionService}; this class only resolves the current user,
 * delegates, and maps domain/validation errors to the shared {@link ApiErrorResponse} contract
 * (ADR-3 — no global {@code @ControllerAdvice}, controller-local handlers only).
 */
@RestController
public class SkillsMarketplaceController implements SkillsMarketplaceApi {

    private final SkillsMarketplaceConnectionService service;

    public SkillsMarketplaceController(SkillsMarketplaceConnectionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<SkillsMarketplaceConnectionDto>> listSkillsMarketplaces() {
        return ResponseEntity.ok(service.list());
    }

    @Override
    public ResponseEntity<SkillsMarketplaceConnectionDto> createSkillsMarketplace(SkillsMarketplaceConnectionRequest request) {
        var dto = service.create(request, currentUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    public ResponseEntity<SkillsMarketplaceConnectionDto> updateSkillsMarketplace(UUID id, SkillsMarketplaceConnectionRequest request) {
        var dto = service.update(id, request, currentUsername());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> deleteSkillsMarketplace(UUID id) {
        service.delete(id, currentUsername());
        return ResponseEntity.noContent().build();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString("preferred_username")
                : "unknown";
    }

    @ExceptionHandler(SkillsMarketplaceException.class)
    public ResponseEntity<ApiErrorResponse> handleSkillsMarketplaceException(SkillsMarketplaceException e) {
        HttpStatus status = switch (e.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NAME_CONFLICT -> HttpStatus.CONFLICT;
            case API_KEY_REQUIRED -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiErrorResponse(e.getCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "Validation failed";
        return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR", message));
    }
}
