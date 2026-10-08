package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.SpecQueueApi;
import nl.metafactory.aicontrol.model.ApiErrorResponse;
import nl.metafactory.aicontrol.model.SpecQueueDto;
import nl.metafactory.aicontrol.model.SpecQueueEnqueueRequest;
import nl.metafactory.aicontrol.model.SpecQueueItemDto;
import nl.metafactory.aicontrol.model.SpecQueueItemUpdateRequest;
import nl.metafactory.aicontrol.model.SpecQueueOrderRequest;
import nl.metafactory.aicontrol.model.SpecQueueSettingsDto;
import nl.metafactory.aicontrol.model.SpecQueueSettingsRequest;
import nl.metafactory.aicontrol.specqueue.app.CurrentActor;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueRoles;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin controller over {@link SpecQueueService}. Exception handlers are controller-local on
 * purpose: access denial must reach Spring Security (403) and unexpected failures stay 500.
 */
@RestController
public class SpecQueueController implements SpecQueueApi {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final SpecQueueService service;

    public SpecQueueController(SpecQueueService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<SpecQueueDto> getSpecQueue(UUID projectId) {
        return ResponseEntity.ok(service.getQueue(projectId));
    }

    @Override
    public ResponseEntity<SpecQueueItemDto> enqueueSpecQueueItem(UUID projectId, SpecQueueEnqueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.enqueue(projectId, request, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueItemDto> updateSpecQueueItem(UUID projectId, UUID itemId,
                                                                SpecQueueItemUpdateRequest request) {
        return ResponseEntity.ok(service.updateItem(projectId, itemId, request, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueItemDto> removeSpecQueueItem(UUID projectId, UUID itemId) {
        return ResponseEntity.ok(service.removeItem(projectId, itemId, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueDto> reorderSpecQueue(UUID projectId, SpecQueueOrderRequest request) {
        return ResponseEntity.ok(service.reorder(projectId, request.itemIds(), CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueDto> pauseSpecQueue(UUID projectId) {
        return ResponseEntity.ok(service.pause(projectId, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueDto> resumeSpecQueue(UUID projectId) {
        return ResponseEntity.ok(service.resume(projectId, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueItemDto> retrySpecQueueItem(UUID projectId, UUID itemId) {
        return ResponseEntity.ok(service.retry(projectId, itemId, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueItemDto> skipSpecQueueItem(UUID projectId, UUID itemId) {
        return ResponseEntity.ok(service.skip(projectId, itemId, CurrentActor.fromSecurityContext()));
    }

    @Override
    public ResponseEntity<SpecQueueSettingsDto> getSpecQueueSettings(UUID projectId) {
        return ResponseEntity.ok(service.getSettings(projectId));
    }

    @Override
    @PreAuthorize("hasRole('" + SpecQueueRoles.ADMIN + "')")
    public ResponseEntity<SpecQueueSettingsDto> updateSpecQueueSettings(UUID projectId,
                                                                        SpecQueueSettingsRequest request) {
        return ResponseEntity.ok(service.updateSettings(
                projectId, request.autoMergeAllowed(), CurrentActor.fromSecurityContext()));
    }

    @ExceptionHandler(SpecQueueException.class)
    public ResponseEntity<ApiErrorResponse> handleSpecQueueException(SpecQueueException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(new ApiErrorResponse(e.getCode().name(), e.getMessage()));
    }

    /** Field names and messages only; rejected values are never echoed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<String> parts = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            parts.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        for (ObjectError globalError : e.getBindingResult().getGlobalErrors()) {
            parts.add(globalError.getDefaultMessage());
        }
        String message = "Request validation failed: " + String.join("; ", parts);
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        return validationError(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return validationError("Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationError("Path parameter " + e.getName() + " has an invalid format");
    }

    private static ResponseEntity<ApiErrorResponse> validationError(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(SpecQueueException.Code.VALIDATION_ERROR.name(), message));
    }
}
