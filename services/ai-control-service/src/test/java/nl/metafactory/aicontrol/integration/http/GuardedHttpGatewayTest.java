package nl.metafactory.aicontrol.integration.http;

import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MockWebServer binds to 127.0.0.1 (loopback) — so every happy-path case here constructs {@link
 * SkillsMarketplaceProperties} with {@code allowPrivateAddresses = true}; only the blocked-path
 * cases use {@code false} (R-C, non-negotiable per the work plan).
 */
class GuardedHttpGatewayTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private SkillsMarketplaceProperties properties(boolean allowPrivate) {
        var properties = new SkillsMarketplaceProperties();
        properties.getEgress().setAllowPrivateAddresses(allowPrivate);
        return properties;
    }

    private GuardedHttpGateway gateway(SkillsMarketplaceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new GuardedHttpGateway(httpClient, properties, new EgressGuard(properties));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void successfulGetReturnsResponseWithStatusContentTypeAndBody() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[{\"name\":\"a\"}]"));

        var result = gateway(properties(true)).get(server.url("/skills").uri(),
                Map.of("Accept", "application/json"), "conn-1", "Acme Skills");

        assertThat(result).isInstanceOf(OutboundHttpResult.Response.class);
        var response = (OutboundHttpResult.Response) result;
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(new String(response.body())).isEqualTo("[{\"name\":\"a\"}]");
    }

    // ── AC-28: blocked at the choke point, before any request ───────────────

    @Test
    void deniedTargetNeverSendsAnyRequest() throws InterruptedException {
        var result = gateway(properties(false)).get(server.url("/skills").uri(),
                Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.BLOCKED_BY_POLICY, null));
        assertThat(server.getRequestCount()).isZero();
    }

    // ── AC-29: redirect re-validation ────────────────────────────────────────

    @Test
    void redirectToAllowedLoopbackTargetIsFollowedAndRevalidated() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/skills-v2").toString()));
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]"));

        var result = gateway(properties(true)).get(server.url("/skills").uri(),
                Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isInstanceOf(OutboundHttpResult.Response.class);
        assertThat(((OutboundHttpResult.Response) result).status()).isEqualTo(200);
        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server.takeRequest().getPath()).isEqualTo("/skills");
        assertThat(server.takeRequest().getPath()).isEqualTo("/skills-v2");
    }

    @Test
    void redirectToBlockedMetadataAddressIsNotFollowedAndExactlyOneRequestIsRecorded() {
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", "http://169.254.169.254/latest/meta-data/"));

        var result = gateway(properties(true)).get(server.url("/skills").uri(),
                Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.BLOCKED_BY_POLICY, null));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void redirectBudgetExhaustionReturnsTooManyRedirects() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/hop-2").toString()));
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/hop-3").toString()));

        var properties = properties(true);
        properties.setMaxRedirects(1);
        var result = gateway(properties).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TOO_MANY_REDIRECTS, 302));
    }

    @Test
    void redirectWithoutLocationHeaderIsMalformedTarget() {
        server.enqueue(new MockResponse().setResponseCode(302));

        var result = gateway(properties(true)).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.MALFORMED_TARGET, 302));
    }

    @Test
    void redirectWithUnresolvableLocationIsMalformedTarget() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "http://exa mple.com/bad uri"));

        var result = gateway(properties(true)).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.MALFORMED_TARGET, 302));
    }

    // ── AC-14: response size cap by streaming abort ─────────────────────────

    @Test
    void oversizedBodyIsAbortedWithoutBufferingTheFullResponse() {
        // A chunked body carries no Content-Length header, so this exercises the streaming-loop
        // cap (as opposed to oversizedContentLengthShortCircuitsBeforeReadingBody, which exercises
        // the declared-Content-Length short-circuit).
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setChunkedBody("x".repeat(2000), 64));

        var properties = properties(true);
        properties.setMaxResponseBytes(100);
        var result = gateway(properties).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.RESPONSE_TOO_LARGE, 200));
    }

    @Test
    void oversizedContentLengthShortCircuitsBeforeReadingBody() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("small")
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Length", "999999"));

        var properties = properties(true);
        properties.setMaxResponseBytes(100);
        var result = gateway(properties).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.RESPONSE_TOO_LARGE, 200));
    }

    // Note: a non-numeric Content-Length header cannot be exercised as a distinct code path here.
    // The JDK's own HTTP/1.1 framing layer parses and validates Content-Length while assembling
    // the response; a malformed value fails httpClient.send() itself (empirically verified) before
    // any HttpResponse object — and therefore this gateway's header inspection — ever exists.

    // ── connectivity failures ────────────────────────────────────────────────

    @Test
    void connectionRefusedIsClassifiedAsUnreachable() throws IOException {
        URI deadUri = server.url("/skills").uri();
        server.shutdown();

        var result = gateway(properties(true)).get(deadUri, Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.UNREACHABLE, null));
    }

    @Test
    void slowResponseExceedingRequestTimeoutIsClassifiedAsTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setBody("[]"));

        var properties = properties(true);
        properties.setRequestTimeoutSeconds(1);
        var result = gateway(properties).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        // Headers (status 200) had already arrived before the deadline was exceeded during body
        // transfer, so the classification carries the status that was actually received.
        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TIMEOUT, 200));
    }

    @Test
    void slowHeadersExceedingRequestTimeoutIsClassifiedAsTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeadersDelay(3, TimeUnit.SECONDS)
                .setBody("[]"));

        var properties = properties(true);
        properties.setRequestTimeoutSeconds(1);
        var result = gateway(properties).get(server.url("/skills").uri(), Map.of(), "conn-1", "Acme Skills");

        // The response never arrived at all, so no status is available (HttpTimeoutException from
        // httpClient.send() itself, not the deadline-during-body-read path above).
        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TIMEOUT, null));
    }

    @Test
    void unexpectedReadFailureIsClassifiedAsUnreachable() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(mockResponse.body()).thenThrow(new RuntimeException("boom - simulated unexpected read failure"));

        HttpClient mockClient = mock(HttpClient.class);
        org.mockito.Mockito.doReturn(mockResponse).when(mockClient).send(any(), any());

        var properties = properties(true);
        var gateway = new GuardedHttpGateway(mockClient, properties, new EgressGuard(properties));

        var result = gateway.get(URI.create("http://127.0.0.1:1/skills"), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.UNREACHABLE, 200));
    }

    @Test
    void closeFailureOnARedirectBodyIsSwallowed() throws Exception {
        InputStream throwsOnClose = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("boom - simulated close failure");
            }
        };

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> mockRedirect = mock(HttpResponse.class);
        when(mockRedirect.statusCode()).thenReturn(302);
        when(mockRedirect.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", java.util.List.of("http://169.254.169.254/latest/meta-data/")), (a, b) -> true));
        when(mockRedirect.body()).thenReturn(throwsOnClose);

        HttpClient mockClient = mock(HttpClient.class);
        org.mockito.Mockito.doReturn(mockRedirect).when(mockClient).send(any(), any());

        var properties = properties(true);
        var gateway = new GuardedHttpGateway(mockClient, properties, new EgressGuard(properties));

        var result = gateway.get(URI.create("http://127.0.0.1:1/skills"), Map.of(), "conn-1", "Acme Skills");

        assertThat(result).isEqualTo(new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.BLOCKED_BY_POLICY, null));
    }

    // ── headers are actually sent ────────────────────────────────────────────

    @Test
    void suppliedHeadersAreSentOnTheRequest() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]")
                .addHeader("Content-Type", "application/json"));

        gateway(properties(true)).get(server.url("/skills").uri(),
                Map.of("Authorization", "Bearer secret-key", "Accept", "application/json"),
                "conn-1", "Acme Skills");

        var recorded = server.takeRequest();
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer secret-key");
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/json");
    }
}
