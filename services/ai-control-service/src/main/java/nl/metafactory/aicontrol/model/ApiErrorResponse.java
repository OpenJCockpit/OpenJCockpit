package nl.metafactory.aicontrol.model;

/**
 * Shared machine-readable error body for additive resources that do not go through a global
 * {@code @ControllerAdvice} (see ADR-3 in the skills-marketplace-settings architecture doc).
 * {@code message} must always be non-blank so the dashboard's {@code errorMessageFrom} helper
 * can render it.
 */
public record ApiErrorResponse(String code, String message) {}
