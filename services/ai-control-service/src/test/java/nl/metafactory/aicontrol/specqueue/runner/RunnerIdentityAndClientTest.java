package nl.metafactory.aicontrol.specqueue.runner;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.MACVerifier;
import nl.metafactory.aicontrol.client.WorkflowStartInputDto;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.config.SpecQueueRunnerWebClientConfig;
import nl.metafactory.aicontrol.specqueue.runner.EmbabelRunnerClient.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class RunnerIdentityAndClientTest {

    private static final String SECRET = "test-only-spec-queue-runner-secret-32-bytes-minimum";
    private MockWebServer server;
    private SpecQueueProperties props;
    private EmbabelRunnerClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        props = new SpecQueueProperties();
        props.getRunner().getToken().setSecret(SECRET);
        props.getRunner().setEmbabelTimeout(Duration.ofSeconds(2));
        client = clientFor(props);
    }

    private EmbabelRunnerClient clientFor(SpecQueueProperties p) {
        var issuer = new RunnerTokenIssuer(p);
        WebClient wc = new SpecQueueRunnerWebClientConfig().embabelRunnerWebClient(server.url("/").toString(), p, issuer);
        return new EmbabelRunnerClient(wc, p);
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

    // ---- token issuer
    @Test
    void tokenMeetsContractAndVerifies() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00.500Z"), ZoneOffset.UTC);
        var issuer = new RunnerTokenIssuer(props, clock);
        var jwt = SignedJWT.parse(issuer.issueToken());
        var c = jwt.getJWTClaimsSet();
        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(c.getIssuer()).isEqualTo(props.getRunner().getToken().getIssuer());
        assertThat(c.getAudience()).containsExactly("embabel-agent-service");
        assertThat(c.getSubject()).isEqualTo("spec-queue-runner");
        assertThat(c.getStringClaim("preferred_username")).isEqualTo("spec-queue-runner");
        assertThat(c.getStringClaim("scope")).isEqualTo("spec-queue-runner");
        assertThat(c.getIssueTime().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(c.getExpirationTime().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
        assertThat(SignedJWT.parse(issuer.issueToken()).getJWTClaimsSet().getJWTID()).isNotEqualTo(c.getJWTID());
        assertThat(jwt.verify(new MACVerifier("another-secret-that-is-also-long-enough-xx".getBytes()))).isFalse();
    }

    @Test
    void blankSecretIsUnconfiguredAndLeaksNothing() {
        var p = new SpecQueueProperties();
        var issuer = new RunnerTokenIssuer(p);
        assertThat(issuer.isConfigured()).isFalse();
        assertThatThrownBy(issuer::issueToken).isInstanceOf(RunnerIdentityUnconfiguredException.class)
                .hasMessageNotContaining("secret=");
    }

    // ---- start mapping
    @Test
    void startedSendsRunnerBearerExactlyOnce() throws Exception {
        server.enqueue(json(200, "{\"executionId\":\"run-1\",\"status\":\"running\",\"startedAt\":\"2026-01-01T00:00:00Z\",\"extra\":1}"));
        var r = client.startWorkflow("wf", WorkflowStartInputDto.empty());
        assertThat(r).isInstanceOfSatisfying(StartResult.Started.class, s -> {
            assertThat(s.runId()).isEqualTo("run-1");
            assertThat(s.startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        });
        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/workflows/wf/start");
        assertThat(req.getHeader("Authorization")).startsWith("Bearer ");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void startMappings() {
        server.enqueue(json(200, "{\"executionId\":\"r\",\"status\":\"BLOCKED\",\"message\":\"" + "x".repeat(600) + "\"}"));
        assertThat(client.startWorkflow("wf", null)).isInstanceOfSatisfying(StartResult.Rejected.class,
                r -> assertThat(r.reason()).hasSize(500));
        server.enqueue(json(200, "{\"status\":\"RUNNING\"}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.DECODE));
        server.enqueue(json(200, "{\"executionId\":\" \"}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.DECODE));
        server.enqueue(json(200, "not json"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.DECODE));
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.DECODE));
        server.enqueue(json(401, "{}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.NotSent(NotSentReason.UNAUTHORIZED));
        server.enqueue(json(403, "{}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.NotSent(NotSentReason.FORBIDDEN));
        server.enqueue(json(404, "{}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Rejected(404, null));
        server.enqueue(json(500, "{}"));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.SERVER_ERROR));
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "http://localhost:1/"));
        assertThat(client.startWorkflow("wf", null)).isInstanceOf(StartResult.Ambiguous.class);
    }

    @Test
    void slowResponseIsAmbiguousTimeoutAfterOneRequest() throws Exception {
        props.getRunner().setEmbabelTimeout(Duration.ofSeconds(1));
        client = clientFor(props);
        server.enqueue(json(200, "{\"executionId\":\"r\"}").setHeadersDelay(3, java.util.concurrent.TimeUnit.SECONDS));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.TIMEOUT));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void droppedConnectionAfterSendIsTransport() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.Ambiguous(AmbiguousReason.TRANSPORT));
    }

    @Test
    void refusedConnectionIsNotSent() throws Exception {
        server.shutdown();
        assertThat(client.startWorkflow("wf", null)).isEqualTo(new StartResult.NotSent(NotSentReason.CONNECT_FAILED));
    }

    @Test
    void unconfiguredSendsNothing() {
        var p = new SpecQueueProperties();
        var c = clientFor(p);
        assertThat(c.startWorkflow("wf", null)).isEqualTo(new StartResult.NotSent(NotSentReason.UNCONFIGURED));
        assertThat(server.getRequestCount()).isZero();
    }

    // ---- reads
    @Test
    void getRunAndDefinitionReads() {
        server.enqueue(json(200, "{\"runId\":\"r\",\"status\":\"COMPLETED\"}"));
        assertThat(client.getRun("r")).isInstanceOfSatisfying(RunLookup.Found.class, f -> assertThat(f.run().status()).isEqualTo("COMPLETED"));
        server.enqueue(json(404, "{}"));
        assertThat(client.getRun("r")).isEqualTo(new RunLookup.Unavailable(FailureCode.NOT_FOUND));
        server.enqueue(json(401, "{}"));
        assertThat(client.getRun("r")).isEqualTo(new RunLookup.Unavailable(FailureCode.UNAUTHORIZED));
        server.enqueue(json(503, "{}"));
        assertThat(client.getRun("r")).isEqualTo(new RunLookup.Unavailable(FailureCode.SERVER_ERROR));
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.getRun("r")).isEqualTo(new RunLookup.Unavailable(FailureCode.DECODE));

        server.enqueue(json(404, "{}"));
        assertThat(client.getWorkflow("w")).isEmpty();
        server.enqueue(json(500, "{}"));
        assertThatThrownBy(() -> client.getWorkflow("w")).isInstanceOf(UpstreamUnavailableException.class)
                .hasMessage("Embabel agent service unavailable: SERVER_ERROR");
        server.enqueue(json(200, "{\"id\":\"g\",\"projectName\":\"p\"}"));
        assertThat(client.getWorkflowGroup("g")).hasValueSatisfying(g -> assertThat(g.projectName()).isEqualTo("p"));
    }

    @Test
    void pollErrorCodes() {
        assertThat(RunnerPollErrorCodes.forEmbabel(FailureCode.FORBIDDEN)).isEqualTo("EMBABEL_UNAUTHORIZED");
        assertThat(RunnerPollErrorCodes.forEmbabel(FailureCode.NOT_FOUND)).isEqualTo("EMBABEL_UNAVAILABLE");
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.UNCONFIGURED)).isEqualTo("RUNNER_NOT_CONFIGURED");
        assertThat(RunnerPollErrorCodes.forNotSent(NotSentReason.CONNECT_FAILED)).isEqualTo("EMBABEL_UNAVAILABLE");
        assertThat(RunnerPollErrorCodes.forGitHub(new nl.metafactory.aicontrol.service.GitHubApiException(429, true))).isEqualTo("GITHUB_RATE_LIMITED");
        assertThat(RunnerPollErrorCodes.forGitHub(new nl.metafactory.aicontrol.service.GitHubApiException(500, false))).isEqualTo("GITHUB_UNAVAILABLE");
    }
}
