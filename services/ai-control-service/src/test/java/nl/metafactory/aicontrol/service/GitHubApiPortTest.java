package nl.metafactory.aicontrol.service;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class GitHubApiPortTest {

    private static final GitHubPullRequestRef REF = new GitHubPullRequestRef("acme", "repo", 7);
    private MockWebServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Function<RecordedRequest, MockResponse> handler;
    private GitHubApiPort port;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                requests.add(r.getMethod() + " " + r.getPath());
                return handler.apply(r);
            }
        });
        server.start();
        port = new GitHubApiPort(Duration.ofSeconds(3));
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private String api() {
        return server.url("/api/v3").toString().replaceAll("/$", "");
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static final String REPO = "{\"id\":1,\"name\":\"repo\",\"full_name\":\"acme/repo\",\"owner\":{\"login\":\"acme\"}}";

    private static String pr(String state, boolean merged, boolean draft) {
        return "{\"number\":7,\"state\":\"" + state + "\",\"merged\":" + merged + ",\"draft\":" + draft
                + ",\"mergeable\":true,\"mergeable_state\":\"clean\",\"head\":{\"sha\":\"abc\",\"ref\":\"f\"},\"base\":{\"ref\":\"main\"}}";
    }

    @Test
    void constructorValidatesTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GitHubApiPort(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new GitHubApiPort(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new GitHubApiPort(Duration.ofSeconds(-1)));
        assertThatCode(() -> new GitHubApiPort(Duration.ofDays(1_000_000))).doesNotThrowAnyException();
    }

    @Test
    void readsOpenPullRequestWithCheckTally() throws Exception {
        handler = r -> {
            String p = r.getPath();
            if (p.endsWith("/repos/acme/repo")) return json(200, REPO);
            if (p.contains("/pulls/7")) return json(200, pr("open", false, false));
            if (p.contains("/check-runs")) {
                return json(200, "{\"total_count\":3,\"check_runs\":["
                        + "{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\"},"
                        + "{\"id\":2,\"status\":\"in_progress\"},"
                        + "{\"id\":3,\"status\":\"completed\",\"conclusion\":\"failure\"}]}");
            }
            if (p.contains("/statuses")) {
                return json(200, "[{\"id\":2,\"state\":\"success\",\"context\":\"ci\",\"created_at\":\"2026-01-02T00:00:00Z\"},"
                        + "{\"id\":1,\"state\":\"failure\",\"context\":\"ci\",\"created_at\":\"2026-01-01T00:00:00Z\"}]");
            }
            return json(404, "{}");
        };
        var state = port.readPullRequest(REF, api(), "tok");
        assertThat(state.open()).isTrue();
        assertThat(state.headSha()).isEqualTo("abc");
        assertThat(state.baseRef()).isEqualTo("main");
        assertThat(state.checksPending()).isEqualTo(1);
        assertThat(state.checksFailing()).isEqualTo(1);
        assertThat(state.checksSucceeded()).isEqualTo(2);
    }

    @Test
    void mergedPullRequestMakesNoCheckCalls() throws Exception {
        handler = r -> r.getPath().endsWith("/repos/acme/repo") ? json(200, REPO) : json(200, pr("closed", true, false));
        var state = port.readPullRequest(REF, api(), "tok");
        assertThat(state.merged()).isTrue();
        assertThat(requests).noneMatch(s -> s.contains("check-runs") || s.contains("statuses"));
    }

    @Test
    void missingPullRequestIs404() {
        handler = r -> r.getPath().endsWith("/repos/acme/repo") ? json(200, REPO) : json(404, "{\"message\":\"secret\"}");
        assertThatThrownBy(() -> port.readPullRequest(REF, api(), "tok"))
                .isInstanceOfSatisfying(GitHubApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(404);
                    assertThat(e.rateLimited()).isFalse();
                    assertThat(e.getMessage()).doesNotContain("secret");
                });
    }

    @Test
    void squashMergeSendsShaMethodAndMessage() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        handler = r -> {
            if (r.getPath().endsWith("/repos/acme/repo")) return json(200, REPO);
            if (r.getMethod().equals("PUT")) {
                bodies.add(r.getBody().readUtf8());
                return json(200, "{\"merged\":true}");
            }
            return json(200, pr("open", false, false));
        };
        port.squashMergePullRequest(REF, "abc", "spec-queue: s.md", api(), "tok");
        assertThat(bodies).hasSize(1);
        assertThat(bodies.get(0)).contains("\"sha\":\"abc\"").contains("squash").contains("spec-queue: s.md");
    }

    @Test
    void mergeRefusalKeepsStatusAndBlankShaSendsNothing() {
        handler = r -> r.getPath().endsWith("/repos/acme/repo") ? json(200, REPO)
                : r.getMethod().equals("PUT") ? json(409, "{\"message\":\"Head branch was modified\"}") : json(200, pr("open", false, false));
        assertThatThrownBy(() -> port.squashMergePullRequest(REF, "abc", "m", api(), "tok"))
                .isInstanceOfSatisfying(GitHubApiException.class, e -> assertThat(e.status()).isEqualTo(409));
        int before = requests.size();
        assertThatIllegalArgumentException().isThrownBy(() -> port.squashMergePullRequest(REF, " ", "m", api(), "tok"));
        assertThat(requests).hasSize(before);
    }

    @Test
    void tooManyRequestsIsRateLimitedWithoutSleeping() {
        handler = r -> json(429, "{\"message\":\"slow down\"}").setHeader("Retry-After", "30");
        long start = System.nanoTime();
        assertThatThrownBy(() -> port.readPullRequest(REF, api(), "tok"))
                .isInstanceOfSatisfying(GitHubApiException.class, e -> assertThat(e.rateLimited()).isTrue());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void unreachableHostIsStatusZero() throws Exception {
        String url = api();
        server.shutdown();
        assertThatThrownBy(() -> port.readPullRequest(REF, url, "tok"))
                .isInstanceOfSatisfying(GitHubApiException.class, e -> {
                    assertThat(e.status()).isZero();
                    assertThat(e.getMessage()).startsWith("GitHub API transport failure: ");
                });
    }
}
