package nl.metafactory.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("preferred_username", "alice")
                .subject("sub-123")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(CurrentUser.username()).isEqualTo("alice");
        assertThat(CurrentUser.subject()).isEqualTo("sub-123");
    }

    @Test
    void returnsUnknownWithoutAuthentication() {
        assertThat(CurrentUser.username()).isEqualTo("unknown");
        assertThat(CurrentUser.subject()).isEqualTo("unknown");
    }

    @Test
    void returnsUnknownForNonJwtPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "pw"));

        assertThat(CurrentUser.username()).isEqualTo("unknown");
    }
}
