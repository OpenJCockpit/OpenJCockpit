package nl.metafactory.common.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * {@link RestClient}s for internal service-to-service calls: a configurable base URL (a compose service
 * name, another host, or an HTTPS endpoint — never assumed), explicit timeouts, no redirects, and every
 * request authenticated with the calling service's client-credentials token. Without a configured service
 * identity a request is not sent at all (fail closed).
 */
public final class ServiceRestClients {

    private ServiceRestClients() {
    }

    public static RestClient create(String baseUrl, ClientCredentialsTokens tokens, Duration connectTimeout,
                                    Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(factory)
                .requestInterceptor(bearer(tokens))
                .build();
    }

    /** Adds the service identity's {@code Authorization}; refuses to send when there is none. */
    static ClientHttpRequestInterceptor bearer(ClientCredentialsTokens tokens) {
        return (request, body, execution) -> {
            String authorization = tokens.authorization()
                    .orElseThrow(() -> new IllegalStateException("no service identity configured for internal calls"));
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            return execution.execute(request, body);
        };
    }

    /** An absolute {@code http(s)} URL without trailing slashes. */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("base URL is not configured");
        }
        String trimmed = baseUrl.strip().replaceAll("/+$", "");
        URI uri = URI.create(trimmed);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) {
            throw new IllegalArgumentException("base URL must be an absolute http(s) URL");
        }
        return trimmed;
    }
}
