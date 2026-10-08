package nl.metafactory.agents.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCurrentUserProviderTest {

    private final JwtCurrentUserProvider provider = new JwtCurrentUserProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsEmptyWhenNoAuthenticationIsPresent() {
        SecurityContextHolder.clearContext();

        assertThat(provider.currentUsername()).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyWhenPrincipalIsNotAJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "password"));

        assertThat(provider.currentUsername()).isEqualTo(Optional.empty());
    }

    @Test
    void fallsBackToSubClaimWhenPreferredUsernameIsAbsent() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "sub-alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.currentUsername()).isEqualTo(Optional.of("sub-alice"));
    }

    @Test
    void fallsBackToSubClaimWhenPreferredUsernameIsBlank() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("preferred_username", "   ")
                .claim("sub", "sub-alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.currentUsername()).isEqualTo(Optional.of("sub-alice"));
    }

    @Test
    void returnsEmptyWhenNeitherPreferredUsernameNorSubClaimIsPresent() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("email", "someone@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.currentUsername()).isEqualTo(Optional.empty());
    }

    @Test
    void returnsThePreferredUsernameClaimWhenPresent() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("preferred_username", "alice")
                .claim("sub", "sub-alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.currentUsername()).isEqualTo(Optional.of("alice"));
    }
}
