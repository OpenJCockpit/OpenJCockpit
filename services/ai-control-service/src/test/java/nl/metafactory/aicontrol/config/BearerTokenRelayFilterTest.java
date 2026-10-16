package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BearerTokenRelayFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static List<String> authorizationSent(ClientRequest request) {
        var sent = new AtomicReference<ClientRequest>();
        new BearerTokenRelayFilter().filter(request, r -> {
            sent.set(r);
            return Mono.just(mock(ClientResponse.class));
        }).block();
        return sent.get().headers().get(HttpHeaders.AUTHORIZATION);
    }

    private static void userSignedIn() {
        var jwt = Jwt.withTokenValue("user-token").header("alg", "RS256").subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void relaysTheUsersToken() {
        userSignedIn();

        assertThat(authorizationSent(ClientRequest.create(HttpMethod.GET, URI.create("http://e/api")).build()))
                .containsExactly("Bearer user-token");
    }

    @Test
    void aServiceCallMadeWhileServingAUserKeepsOnlyItsOwnToken() {
        userSignedIn();

        var serviceCall = ClientRequest.create(HttpMethod.POST, URI.create("http://e/internal"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer svc").build();

        assertThat(authorizationSent(serviceCall)).containsExactly("Bearer svc");
    }
}
