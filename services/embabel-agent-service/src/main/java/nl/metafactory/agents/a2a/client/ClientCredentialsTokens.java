package nl.metafactory.agents.a2a.client;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * OAuth2 client-credentials tokens for calling agents through an A2A provider, cached until shortly
 * before expiry. The token is never logged.
 */
public class ClientCredentialsTokens {

    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final A2AProvidersProperties.Provider.CallerAuth config;
    private final RestClient rest;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokens(A2AProvidersProperties.Provider.CallerAuth config, Clock clock) {
        this.config = config;
        this.clock = clock;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    /** {@code Bearer <token>}, or empty when no caller identity is configured. */
    public synchronized Optional<String> authorization() {
        if (!config.configured()) {
            return Optional.empty();
        }
        if (token == null || !clock.instant().isBefore(expiresAt.minus(EXPIRY_MARGIN))) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", config.getClientId());
            form.add("client_secret", config.getClientSecret());
            byte[] body = rest.post().uri(config.getTokenUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(byte[].class);
            JsonNode response = json.readTree(body);
            String accessToken = response.path("access_token").asString(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("token endpoint returned no access_token");
            }
            token = accessToken;
            expiresAt = clock.instant().plusSeconds(response.path("expires_in").asLong(60));
        }
        return Optional.of("Bearer " + token);
    }
}
