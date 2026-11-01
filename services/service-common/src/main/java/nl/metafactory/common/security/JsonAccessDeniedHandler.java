package nl.metafactory.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 403 for an authenticated caller lacking the required role: the standard bearer-token
 * {@code WWW-Authenticate} challenge (from {@link BearerTokenAccessDeniedHandler}) plus an {@code ApiErrorResponse}
 * body ({@code code}, {@code message}) the dashboard can show. Never reveals which role was required.
 */
public final class JsonAccessDeniedHandler implements AccessDeniedHandler {

    static final String BODY = "{\"code\":\"FORBIDDEN\",\"message\":"
            + "\"You do not have permission to perform this action. Ask an administrator for the required role.\"}";

    /** Structured security audit trail (one line per refused request; principal and route, never the token). */
    private static final Logger AUDIT = LoggerFactory.getLogger("openjcockpit.audit");

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        AUDIT.warn("AUDIT access-denied principal={} method={} path={}",
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                request.getMethod(), request.getRequestURI());
        bearer.handle(request, response, accessDeniedException);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(BODY);
    }
}
