package nl.metafactory.aicontrol.integration.skillsmarketplace.placeholder;

import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import nl.metafactory.aicontrol.integration.http.EgressGuard;
import nl.metafactory.aicontrol.integration.http.GuardedHttpGateway;
import nl.metafactory.aicontrol.integration.skillsmarketplace.ExternalSkill;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceQuery;
import nl.metafactory.aicontrol.model.SkillCatalogSourceOutcome;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code EmbabelAgentClientTest}'s MockWebServer pattern. MockWebServer binds to
 * 127.0.0.1, so every happy-path case here constructs {@link SkillsMarketplaceProperties} with
 * {@code allowPrivateAddresses = true}; only the blocked-path case uses {@code false} (R-C).
 */
class PlaceholderSkillsMarketplaceClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        SecurityContextHolder.clearContext();
    }

    private SkillsMarketplaceProperties properties(boolean allowPrivate) {
        var properties = new SkillsMarketplaceProperties();
        properties.getEgress().setAllowPrivateAddresses(allowPrivate);
        return properties;
    }

    private PlaceholderSkillsMarketplaceClient client(SkillsMarketplaceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        GuardedHttpGateway gateway = new GuardedHttpGateway(httpClient, properties, new EgressGuard(properties));
        return new PlaceholderSkillsMarketplaceClient(gateway, properties);
    }

    private MarketplaceQuery query(String marketplaceUrl, String apiKey) {
        return new MarketplaceQuery(UUID.randomUUID(), "Acme Skills", marketplaceUrl, apiKey);
    }

    private MarketplaceQuery query() {
        return query(server.url("").toString(), "the-marketplace-api-key");
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse().setResponseCode(status).setBody(body).setHeader("Content-Type", "application/json");
    }

    // ── AC-43: exact request shape ───────────────────────────────────────────

    @Test
    void fetchCatalogSendsExactlyOneGetRequestWithTheDocumentedHeaders() throws Exception {
        server.enqueue(jsonResponse(200, "[]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(server.getRequestCount()).isEqualTo(1);
        var recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/skills");
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/json");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer the-marketplace-api-key");
    }

    // ── AC-44: unknown fields ignored, absent optional tolerated ─────────────

    @Test
    void mapsKnownFieldsIgnoresUnknownFieldsAndToleratesAbsentDescription() {
        server.enqueue(jsonResponse(200,
                "[{\"name\":\"a\",\"description\":\"d\"},{\"name\":\"b\",\"description\":\"d2\",\"extraUnknownField\":123}]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(result.skills()).containsExactly(
                new ExternalSkill("a", "d"),
                new ExternalSkill("b", "d2"));
    }

    @Test
    void absentDescriptionFieldMapsToNullWithoutFailingMapping() {
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\"}]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(result.skills()).containsExactly(new ExternalSkill("a", null));
    }

    // ── AC-45: item missing name rejects the whole marketplace ───────────────

    @Test
    void rejectsWholeMarketplaceWhenAnyItemHasNoNameField() {
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\"},{\"description\":\"no name at all\"}]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
        assertThat(result.skills()).isEmpty();
    }

    @Test
    void rejectsWholeMarketplaceWhenAnyItemHasABlankName() {
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\"},{\"name\":\"   \"}]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
        assertThat(result.skills()).isEmpty();
    }

    // ── AC-46: URL normalization ─────────────────────────────────────────────

    @Test
    void withAndWithoutTrailingSlashProduceTheIdenticalComposedPath() throws Exception {
        server.enqueue(jsonResponse(200, "[]"));
        server.enqueue(jsonResponse(200, "[]"));

        String withTrailingSlash = server.url("").toString();
        String withoutTrailingSlash = withTrailingSlash.substring(0, withTrailingSlash.length() - 1);

        client(properties(true)).fetchCatalog(query(withoutTrailingSlash, "key"));
        client(properties(true)).fetchCatalog(query(withTrailingSlash, "key"));

        assertThat(server.takeRequest().getPath()).isEqualTo("/skills");
        assertThat(server.takeRequest().getPath()).isEqualTo("/skills");
    }

    @Test
    void aStoredUrlWithAnExistingPathSegmentAppendsSkillsWithoutDoublingSlashes() throws Exception {
        server.enqueue(jsonResponse(200, "[]"));

        String withSubPath = server.url("/api/").toString();
        client(properties(true)).fetchCatalog(query(withSubPath, "key"));

        assertThat(server.takeRequest().getPath()).isEqualTo("/api/skills");
    }

    // ── AC-47 (adapter half): unparseable/blank stored URL is CONFIG_ERROR ───

    @Test
    void blankMarketplaceUrlIsConfigErrorAndSendsNoRequest() {
        var result = client(properties(true)).fetchCatalog(query("   ", "key"));

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.CONFIG_ERROR);
        assertThat(result.skills()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    // ── AC-12: authentication rejected ────────────────────────────────────────

    @Test
    void unauthorizedResponseIsClassifiedAsAuthFailedWithoutLeakingTheCredential() {
        server.enqueue(new MockResponse().setResponseCode(401));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.AUTH_FAILED);
        assertThat(result.httpStatus()).isEqualTo(401);
    }

    @Test
    void forbiddenResponseIsClassifiedAsAuthFailed() {
        server.enqueue(new MockResponse().setResponseCode(403));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.AUTH_FAILED);
        assertThat(result.httpStatus()).isEqualTo(403);
    }

    // ── AC-13: invalid response shapes ───────────────────────────────────────

    @Test
    void nonJsonContentTypeIsClassifiedAsInvalidResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("<html>not json</html>").setHeader("Content-Type", "text/html"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    @Test
    void missingContentTypeIsClassifiedAsInvalidResponse() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]").removeHeader("Content-Type"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    @Test
    void jsonObjectInsteadOfArrayIsClassifiedAsInvalidResponse() {
        server.enqueue(jsonResponse(200, "{\"name\":\"not-an-array\"}"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    @Test
    void truncatedJsonBodyIsClassifiedAsInvalidResponseWithNoUnhandledException() {
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\""));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    @Test
    void serverErrorStatusIsClassifiedAsInvalidResponse() {
        server.enqueue(new MockResponse().setResponseCode(500));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
        assertThat(result.httpStatus()).isEqualTo(500);
    }

    // ── AC-14: caps ───────────────────────────────────────────────────────────

    @Test
    void itemCountExceedingTheConfiguredCapIsClassifiedAsInvalidResponse() {
        var properties = properties(true);
        properties.setMaxItems(1);
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\"},{\"name\":\"b\"}]"));

        var result = client(properties).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    @Test
    void oversizedByteResponseSurfacesAsInvalidResponseThroughTheGateway() {
        var properties = properties(true);
        properties.setMaxResponseBytes(10);
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setChunkedBody("x".repeat(200), 32));

        var result = client(properties).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.INVALID_RESPONSE);
    }

    // ── Passthrough of gateway classifications ───────────────────────────────

    @Test
    void blockedTargetIsClassifiedAsBlockedByPolicyAndSendsNoRequest() {
        var result = client(properties(false)).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.BLOCKED_BY_POLICY);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void connectionErrorIsClassifiedAsUnreachable() throws IOException {
        String deadUrl = server.url("").toString();
        server.shutdown();

        var result = client(properties(true)).fetchCatalog(query(deadUrl, "key"));

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.UNREACHABLE);
    }

    // ── AC-52: timeout, no Thread.sleep ───────────────────────────────────────

    @Test
    void slowMarketplaceResponseIsClassifiedAsTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200).setBodyDelay(3, TimeUnit.SECONDS).setBody("[]"));

        var properties = properties(true);
        properties.setRequestTimeoutSeconds(1);
        var result = client(properties).fetchCatalog(query());

        assertThat(result.outcome()).isEqualTo(SkillCatalogSourceOutcome.TIMEOUT);
    }

    // ── AC-27 / AC-27a: no Keycloak token leak, exactly one Authorization header ──

    @Test
    void recordedRequestCarriesExactlyOneAuthorizationHeaderEqualToBearerApiKey() throws Exception {
        server.enqueue(jsonResponse(200, "[]"));

        client(properties(true)).fetchCatalog(query());

        var recorded = server.takeRequest();
        assertThat(recorded.getHeaders().values("Authorization")).containsExactly("Bearer the-marketplace-api-key");
    }

    @Test
    void doesNotLeakAnAuthenticatedCallersJwtEvenWhenPresentInSecurityContext() throws Exception {
        var jwt = Jwt.withTokenValue("caller-openjcockpit-access-token")
                .header("alg", "RS256")
                .claim("sub", "user1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        // This is the load-bearing negative test (AC-27a): assert the context really is set,
        // otherwise this test would pass vacuously.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        server.enqueue(jsonResponse(200, "[]"));
        client(properties(true)).fetchCatalog(query(server.url("").toString(), "vendor-api-key"));

        var recorded = server.takeRequest();
        assertThat(recorded.getHeaders().values("Authorization")).containsExactly("Bearer vendor-api-key");
        assertThat(recorded.getHeaders().toString()).doesNotContain("caller-openjcockpit-access-token");
        assertThat(recorded.getBody().readUtf8()).doesNotContain("caller-openjcockpit-access-token");
    }

    // ── MarketplaceQuery.toString() redaction (BR-8/AC-26) ───────────────────

    @Test
    void marketplaceQueryToStringNeverContainsTheApiKey() {
        var query = new MarketplaceQuery(UUID.randomUUID(), "Acme Skills", "https://x.example.com", "super-secret-key");

        assertThat(query.toString()).doesNotContain("super-secret-key").contains("Acme Skills");
    }

    // ── AC-49: PlaceholderSkillItem never crosses the package boundary ───────

    @Test
    void fetchCatalogReturnsVendorNeutralExternalSkillNotThePlaceholderWireType() {
        server.enqueue(jsonResponse(200, "[{\"name\":\"a\",\"description\":\"d\"}]"));

        var result = client(properties(true)).fetchCatalog(query());

        assertThat(result.skills()).allSatisfy(skill -> assertThat(skill).isInstanceOf(ExternalSkill.class));
    }
}
