package nl.metafactory.aicontrol.specqueue.app;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller: stable subject plus a display name (preferred_username, may be null). */
public record CurrentActor(String subject, String displayName) {

    private static final int MAX_SUBJECT = 70;
    private static final int MAX_DISPLAY_NAME = 255;

    public static CurrentActor fromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated JWT principal");
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("JWT has no subject");
        }
        String name = jwt.getClaimAsString("preferred_username");
        return new CurrentActor(shorten(sub), name == null ? null : truncate(name, MAX_DISPLAY_NAME));
    }

    /** Audit label stored in {@code actor VARCHAR(80)}. */
    public String eventLabel() {
        return "USER:" + subject;
    }

    /** Long subjects keep a readable prefix plus a hash suffix, so two of them never share a label. */
    private static String shorten(String subject) {
        if (subject.length() <= MAX_SUBJECT) {
            return subject;
        }
        String hash = UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return subject.substring(0, MAX_SUBJECT - 15) + "~" + hash.substring(0, 14);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
