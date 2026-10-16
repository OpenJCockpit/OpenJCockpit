package nl.metafactory.common.oauth;

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
 * OAuth2 client-credentials tokens of a service identity (a Keycloak confidential client), cached
 * until shortly before expiry. The token and the client secret are never logged.
 */
public class ClientCredentialsTokens {

    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    /** Token endpoint and client; {@link #configured()} is false when any part is blank. */
    public record Config(String tokenUrl, String clientId, String clientSecret) {
        public boolean configured() {
            return notBlank(tokenUrl) && notBlank(clientId) && notBlank(clientSecret);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        @Override
        public String toString() {
            return "Config[tokenUrl=" + tokenUrl + ", clientId=" + clientId + ", clientSecret=***]";
        }
    }

    private final Config config;
    private final RestClient rest;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsTokens(Config config) {
        this(config, Clock.systemUTC(), defaultRestClient());
    }

    ClientCredentialsTokens(Config config, Clock clock, RestClient rest) {
        this.config = config;
        this.clock = clock;
        this.rest = rest;
    }

    static RestClient defaultRestClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return config != null && config.configured();
    }

    /** {@code Bearer <token>}, or empty when no service identity is configured. */
    public synchronized Optional<String> authorization() {
        if (!configured()) {
            return Optional.empty();
        }
        if (token == null || !clock.instant().isBefore(expiresAt.minus(EXPIRY_MARGIN))) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", config.clientId());
            form.add("client_secret", config.clientSecret());
            byte[] body = rest.post().uri(config.tokenUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
