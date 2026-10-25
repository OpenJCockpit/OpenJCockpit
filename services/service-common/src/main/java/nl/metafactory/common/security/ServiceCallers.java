package nl.metafactory.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authorisation of internal, service-to-service endpoints by the caller's Keycloak client ({@code azp}).
 * Network location is never trusted: a request is from a service only when its validated token says so.
 */
public final class ServiceCallers {

    private ServiceCallers() {
    }

    /** True only for a token whose {@code azp} equals the configured, non-blank client id. */
    public static boolean is(Jwt jwt, String expectedClientId) {
        if (jwt == null || expectedClientId == null || expectedClientId.isBlank()) {
            return false;
        }
        return expectedClientId.equals(jwt.getClaimAsString("azp"));
    }

    /** The caller's client id for logging, or {@code null}. */
    public static String clientId(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("azp");
    }
}
