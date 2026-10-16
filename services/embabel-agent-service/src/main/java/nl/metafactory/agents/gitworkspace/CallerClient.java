package nl.metafactory.agents.gitworkspace;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/** The Keycloak client ({@code azp}) of the current caller's access token, if any. */
public final class CallerClient {

    private CallerClient() {
    }

    public static Optional<String> current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return Optional.ofNullable(jwt.getToken().getClaimAsString("azp"));
        }
        return Optional.empty();
    }
}
