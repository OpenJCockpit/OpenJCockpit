package nl.metafactory.agents.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Mirrors the {@code preferred_username} JWT-claim extraction pattern already used by
 * {@code nl.metafactory.agents.api.ApprovalGateController}, but — unlike that controller's private
 * helper, which falls back to the literal string {@code "unknown"} — this returns
 * {@link Optional#empty()} whenever there is no authenticated JWT principal or the claim itself is
 * null/blank, so a caller can never mistake "no identity" for a real, fabricated username.
 * <p>
 * When {@code preferred_username} is absent or blank, this falls back to the {@code sub} claim,
 * returning {@link Optional#empty()} only when neither claim is present/non-blank; it never
 * fabricates a placeholder value (unlike {@code ApprovalGateController}'s {@code "unknown"} fallback,
 * which this class intentionally does not copy). */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return Optional.of(username);
        }
        String sub = jwt.getClaimAsString("sub");
        return (sub == null || sub.isBlank()) ? Optional.empty() : Optional.of(sub);
    }
}
