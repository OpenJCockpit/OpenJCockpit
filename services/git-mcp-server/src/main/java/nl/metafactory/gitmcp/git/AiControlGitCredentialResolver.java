package nl.metafactory.gitmcp.git;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pulls a job workspace's git credentials from ai-control-service
 * ({@code GET /internal/git-workspaces/{workspaceKey}/credentials?repositoryUrl=...}), authenticated with
 * this server's own Keycloak service identity (client credentials). ai-control only answers while the
 * job is active and the repository URL is the job's project repository. Nothing here is cached beyond
 * the service token; credentials are never logged.
 */
public class AiControlGitCredentialResolver implements GitCredentialResolver {

    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final GitToolsProperties.Credentials config;
    private final RestClient rest;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private String serviceToken;
    private Instant serviceTokenExpiresAt = Instant.EPOCH;

    public AiControlGitCredentialResolver(GitToolsProperties.Credentials config) {
        this(config, defaultRestClient(), Clock.systemUTC());
    }

    AiControlGitCredentialResolver(GitToolsProperties.Credentials config, RestClient rest, Clock clock) {
        this.config = config;
        this.rest = rest;
        this.clock = clock;
    }

    static RestClient defaultRestClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<GitCredentials> resolve(String workspaceKey, String repositoryUrl) {
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return Optional.empty();
        }
        if (!config.configured()) {
            throw new IllegalStateException("workspace credentials are not configured on this server");
        }
        byte[] body;
        try {
            body = rest.get()
                    .uri(config.getAiControlUrl().replaceAll("/+$", "")
                                    + "/internal/git-workspaces/{key}/credentials?repositoryUrl={url}",
                            workspaceKey, repositoryUrl)
                    .header("Authorization", serviceAuthorization())
                    .retrieve().body(byte[].class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new IllegalStateException("no active workspace " + workspaceKey + " for this repository");
            }
            throw new IllegalStateException("workspace credentials refused (HTTP " + e.getStatusCode().value() + ")");
        }
        JsonNode node = json.readTree(body == null ? new byte[0] : body);
        String token = node.path("token").asString(null);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String username = node.path("username").asString(null);
        return Optional.of(new GitCredentials(username, token));
    }

    private synchronized String serviceAuthorization() {
        if (serviceToken == null || !clock.instant().isBefore(serviceTokenExpiresAt.minus(EXPIRY_MARGIN))) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", config.getClientId());
            form.add("client_secret", config.getClientSecret());
            byte[] response = rest.post().uri(config.getTokenUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(byte[].class);
            JsonNode node = json.readTree(response);
            String accessToken = node.path("access_token").asString(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("token endpoint returned no access_token");
            }
            serviceToken = accessToken;
            serviceTokenExpiresAt = clock.instant().plusSeconds(node.path("expires_in").asLong(60));
        }
        return "Bearer " + serviceToken;
    }
}
