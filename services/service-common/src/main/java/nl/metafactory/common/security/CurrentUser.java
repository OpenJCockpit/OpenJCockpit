package nl.metafactory.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT claim lookup for the authenticated caller; each accessor yields {@code "unknown"} when there is no JWT principal. */
public final class CurrentUser {

    private static final String UNKNOWN = "unknown";

    private CurrentUser() {
    }

    public static String username() {
        return claim("preferred_username");
    }

    public static String subject() {
        return claim("sub");
    }

    private static String claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                ? jwt.getClaimAsString(name)
                : UNKNOWN;
    }
}
