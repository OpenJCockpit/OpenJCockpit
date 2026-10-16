package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.model.SourceStatus;
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

/**
 * Minimal authenticated GET client for the LiteLLM proxy's management API, used only as a
 * metadata source (never as the model gateway). Every outcome is classified into a
 * {@link SourceStatus}; the API key is sent as a Bearer token and never logged.
 */
public class LiteLlmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(LiteLlmHttpClient.class);
    static final int MAX_BODY_BYTES = 512 * 1024;

    public record Response(SourceStatus status, JsonNode body, String reason) {

        public static Response of(SourceStatus status, String reason) {
            return new Response(status, null, reason);
        }
    }

    private final RestClient rest;
    private final JsonMapper json = JsonMapper.builder().build();

    public LiteLlmHttpClient(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        Objects.requireNonNull(baseUrl, "baseUrl");
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
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /** GET {@code path} (a URI template) and classify the outcome. */
    public Response get(String pathTemplate, Object... uriVariables) {
        return exchange(pathTemplate, () -> rest.get().uri(pathTemplate, uriVariables).retrieve().body(byte[].class));
    }

    /** POST a JSON body to {@code path} and classify the outcome. */
    public Response post(String path, Object body) {
        return post(path, body, java.util.Map.of());
    }

    /** POST a JSON body with extra request headers (values are never logged). */
    public Response post(String path, Object body, java.util.Map<String, String> headers) {
        return exchange(path, () -> rest.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .headers(h -> headers.forEach(h::set))
                .body(json.writeValueAsBytes(body)).retrieve().body(byte[].class));
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
