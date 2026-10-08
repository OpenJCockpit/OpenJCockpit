package nl.metafactory.aicontrol.integration.http;

/**
 * Outcome of one outbound HTTP attempt made through {@link GuardedHttpGateway} — the single
 * outbound path used by marketplace egress. A {@link Response} is returned only for a
 * successfully-received, within-policy HTTP response (any status code); every other case,
 * including a policy denial, is a {@link Failure} carrying a classification the caller maps onto
 * {@code SkillCatalogSourceOutcome}.
 */
public sealed interface OutboundHttpResult permits OutboundHttpResult.Response, OutboundHttpResult.Failure {

    record Response(int status, String contentType, byte[] body) implements OutboundHttpResult {
    }

    record Failure(FailureKind kind, Integer httpStatus) implements OutboundHttpResult {
    }

    enum FailureKind {
        BLOCKED_BY_POLICY,
        TIMEOUT,
        UNREACHABLE,
        RESPONSE_TOO_LARGE,
        TOO_MANY_REDIRECTS,
        MALFORMED_TARGET
    }
}
