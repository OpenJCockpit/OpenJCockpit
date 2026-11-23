package nl.metafactory.common.agentdefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Minimal authenticated GET client for the LiteLLM proxy's management API, used only as a
 * metadata source (never as the model gateway). Every outcome is classified into a
 * {@link SourceStatus}. The virtual key goes in {@code x-litellm-api-key} (LiteLLM reads it before
 * {@code Authorization}; verified against the pinned v1.109.1 source, {@code proxy/auth/user_api_key_auth.py}
 * {@code get_api_key}), so {@code Authorization} is free for the caller's Keycloak token, which a proxy in
 * front of LiteLLM can check. LiteLLM does not forward {@code Authorization} to A2A agents. Neither value is
 * logged.
 */
public class LiteLlmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(LiteLlmHttpClient.class);
    static final int MAX_BODY_BYTES = 512 * 1024;
    /** LiteLLM's header for the virtual key ({@code Bearer <key>}). */
    public static final String API_KEY_HEADER = "x-litellm-api-key";

    public record Response(SourceStatus status, JsonNode body, String reason) {

        public static Response of(SourceStatus status, String reason) {
            return new Response(status, null, reason);
        }
    }

    private final RestClient rest;
    private final Supplier<Optional<String>> authorization;
    private final JsonMapper json = JsonMapper.builder().build();

    /** Without a Keycloak identity: only the virtual key is sent. */
    public LiteLlmHttpClient(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        this(baseUrl, apiKey, connectTimeout, readTimeout, Optional::empty);
    }

    /**
     * @param authorization the {@code Authorization} value per request ({@code Bearer <Keycloak token>}), resolved
     *                      on every request so a refreshed token is used; empty sends none
     */
    public LiteLlmHttpClient(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout,
                             Supplier<Optional<String>> authorization) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("LiteLLM api key is required");
        }
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(API_KEY_HEADER, "Bearer " + apiKey)
                .build();
    }

    /** GET {@code path} (a URI template) and classify the outcome. */
    public Response get(String pathTemplate, Object... uriVariables) {
        return authorized(pathTemplate, auth -> exchange(pathTemplate,
                () -> rest.get().uri(pathTemplate, uriVariables).headers(auth).retrieve().body(byte[].class)));
    }

    /** POST a JSON body to {@code path} and classify the outcome. */
    public Response post(String path, Object body) {
        return post(path, body, java.util.Map.of());
    }

    /** POST a JSON body with extra request headers (values are never logged). */
    public Response post(String path, Object body, java.util.Map<String, String> headers) {
        return authorized(path, auth -> exchange(path, () -> rest.post().uri(path)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(auth).headers(h -> headers.forEach(h::set))
                .body(json.writeValueAsBytes(body)).retrieve().body(byte[].class)));
    }

    /** Resolves the Keycloak token first: without one that should be there, LiteLLM is not called at all. */
    private Response authorized(String path, Function<Consumer<HttpHeaders>, Response> call) {
        Optional<String> value;
        try {
            value = authorization.get();
        } catch (RuntimeException e) {
            log.warn("litellm.authorization.unavailable path={} error={}", path, e.getClass().getSimpleName());
            return Response.of(SourceStatus.UNAVAILABLE, "Keycloak token unavailable");
        }
        return call.apply(headers -> value.ifPresent(v -> headers.set(HttpHeaders.AUTHORIZATION, v)));
    }

    Response exchange(String pathTemplate, java.util.function.Supplier<byte[]> call) {
        try {
            byte[] bytes = call.get();
            if (bytes == null || bytes.length == 0) {
                return Response.of(SourceStatus.INVALID, "empty response");
            }
            if (bytes.length > MAX_BODY_BYTES) {
                return Response.of(SourceStatus.INVALID, "response too large");
            }
            return new Response(SourceStatus.FOUND, json.readTree(bytes), null);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            log.warn("litellm.request.failed path={} status={}", pathTemplate, code);
            return switch (code) {
                case 401, 403 -> Response.of(SourceStatus.UNAUTHORIZED, "HTTP " + code);
                case 404 -> Response.of(SourceStatus.NOT_FOUND, "HTTP 404");
                case 400, 422 -> Response.of(SourceStatus.INVALID, "HTTP " + code);
                default -> Response.of(SourceStatus.UNAVAILABLE, "HTTP " + code);
            };
        } catch (ResourceAccessException e) {
            log.warn("litellm.request.unavailable path={} error={}", pathTemplate,
                    e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
            return Response.of(SourceStatus.UNAVAILABLE, "unreachable or timed out");
        } catch (JacksonException e) {
            return Response.of(SourceStatus.INVALID, "response is not JSON");
        } catch (RestClientException e) {
            log.warn("litellm.request.failed path={} error={}", pathTemplate, e.getClass().getSimpleName());
            return Response.of(SourceStatus.UNAVAILABLE, e.getClass().getSimpleName());
        }
    }
}
