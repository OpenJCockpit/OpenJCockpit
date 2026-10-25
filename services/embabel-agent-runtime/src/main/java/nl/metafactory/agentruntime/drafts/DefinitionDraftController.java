package nl.metafactory.agentruntime.drafts;

import nl.metafactory.agentruntime.config.RuntimeApiProperties;
import nl.metafactory.common.agentruntime.AgentExecutionFailure;
import nl.metafactory.common.agentruntime.AgentRuntimeApi;
import nl.metafactory.common.agentruntime.DefinitionDrafts;
import nl.metafactory.common.security.ServiceCallers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Internal: definition drafts for the workflow designer; only the agent service's identity may call it. */
@RestController
public class DefinitionDraftController {

    private static final Logger log = LoggerFactory.getLogger(DefinitionDraftController.class);
    static final int MAX_CONCEPT_CHARS = 8_000;

    private final DefinitionDraftService drafts;
    private final RuntimeApiProperties properties;

    public DefinitionDraftController(DefinitionDraftService drafts, RuntimeApiProperties properties) {
        this.drafts = drafts;
        this.properties = properties;
    }

    @PostMapping(path = AgentRuntimeApi.DEFINITION_DRAFT, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> draft(@PathVariable String kind, @RequestBody DefinitionDrafts.Request request,
                                   @AuthenticationPrincipal Jwt jwt) {
        if (!ServiceCallers.is(jwt, properties.getTrustedClientId())) {
            log.warn("definition-draft.denied reason=caller-not-allowed caller={}", ServiceCallers.clientId(jwt));
            return failure(HttpStatus.FORBIDDEN, AgentExecutionFailure.FORBIDDEN, "Caller not permitted");
        }
        var parsed = DefinitionDrafts.Kind.fromPathSegment(kind);
        if (parsed.isEmpty() || request.concept() == null || request.concept().isBlank()
                || request.concept().length() > MAX_CONCEPT_CHARS) {
            return failure(HttpStatus.BAD_REQUEST, AgentExecutionFailure.INVALID_REQUEST,
                    "Unknown draft kind or missing/too long concept");
        }
        try {
            return ResponseEntity.ok(drafts.draft(parsed.get(), request.concept()));
        } catch (RuntimeException e) {
            log.warn("definition-draft.failed kind={} error={}", kind, e.getClass().getSimpleName());
            return failure(HttpStatus.BAD_GATEWAY, AgentExecutionFailure.EXECUTION_FAILED,
                    "The LLM could not produce a draft");
        }
    }

    private static ResponseEntity<AgentExecutionFailure> failure(HttpStatus status, String reason, String message) {
        return ResponseEntity.status(status).body(new AgentExecutionFailure(null, reason, message));
    }
}
