package nl.metafactory.aicontrol.service;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.connector.GitHubConnector;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.kohsuke.github.extras.ImpatientHttpConnector;
import org.kohsuke.github.internal.GitHubConnectorHttpConnectorAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLHandshakeException;

/** GitHub REST access for the spec-queue runner: fail-fast limits, bounded timeouts, single-shot merge. */
public final class GitHubApiPort implements GitHubPort {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ITEMS = 500;
    private static final String TOKEN_LABEL = "spec-queue";

    private final int timeoutMillis;

    public GitHubApiPort(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeoutMillis = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
    }

    /** Internal marker carrying the status of a rate-limit response; never sleeps. */
    private static final class RateLimitedException extends IOException {
        private final int status;

        RateLimitedException(int status) {
            super("rate limited");
            this.status = status;
        }
    }

    private GitHub client(String apiUrl, String token) throws IOException {
        HttpConnector impatient = new ImpatientHttpConnector(HttpConnector.DEFAULT, timeoutMillis, timeoutMillis);
        GitHubConnector base = new GitHubConnectorHttpConnectorAdapter(impatient);
        // The library silently retries these transport errors; a merge must be sent at most once.
        GitHubConnector singleShot = request -> {
            try {
                return base.send(request);
            } catch (SocketException | SocketTimeoutException | SSLHandshakeException e) {
                throw new IOException(e.getClass().getSimpleName());
            }
        };
        return new GitHubBuilder()
                .withEndpoint(apiUrl)
                .withOAuthToken(token, TOKEN_LABEL)
                .withConnector(singleShot)
                .withRateLimitHandler(new GitHubRateLimitHandler() {
                    @Override
                    public void onError(GitHubConnectorResponse response) throws IOException {
                        throw new RateLimitedException(response.statusCode());
                    }
                })
                .withAbuseLimitHandler(new GitHubAbuseLimitHandler() {
                    @Override
                    public void onError(GitHubConnectorResponse response) throws IOException {
                        throw new RateLimitedException(response.statusCode());
                    }
                })
                .build();
    }

    @Override
    public void checkRepository(String owner, String repo, String apiUrl, String token) throws Exception {
        client(apiUrl, token).getRepository(owner + "/" + repo);
    }

    @Override
    public GitHubPullRequestState readPullRequest(GitHubPullRequestRef ref, String apiUrl, String token)
            throws GitHubApiException {
        try {
            GitHub github = client(apiUrl, token);
            var repository = github.getRepository(ref.owner() + "/" + ref.repo());
            GHPullRequest pr = repository.getPullRequest(ref.number());
            boolean merged = pr.isMerged();
            boolean open = pr.getState() == GHIssueState.OPEN;
            String sha = pr.getHead().getSha();
            int pending = 0;
            int failing = 0;
            int succeeded = 0;
            if (open && !merged) {
                int seen = 0;
                var runs = repository.getCheckRuns(sha, Map.of()).withPageSize(PAGE_SIZE).iterator();
                while (runs.hasNext() && seen++ < MAX_ITEMS) {
                    GHCheckRun run = runs.next();
                    if (run.getStatus() != GHCheckRun.Status.COMPLETED) {
                        pending++;
                    } else if (run.getConclusion() == GHCheckRun.Conclusion.SUCCESS
                            || run.getConclusion() == GHCheckRun.Conclusion.NEUTRAL
                            || run.getConclusion() == GHCheckRun.Conclusion.SKIPPED) {
                        succeeded++;
                    } else {
                        failing++;
                    }
                }
                seen = 0;
                Map<String, GHCommitStatus> newest = new HashMap<>();
                var statuses = repository.listCommitStatuses(sha).withPageSize(PAGE_SIZE).iterator();
                while (statuses.hasNext() && seen++ < MAX_ITEMS) {
                    GHCommitStatus status = statuses.next();
                    String context = status.getContext() == null ? "" : status.getContext();
                    GHCommitStatus current = newest.get(context);
                    if (current == null || isNewer(status, current)) {
                        newest.put(context, status);
                    }
                }
                for (GHCommitStatus status : newest.values()) {
                    GHCommitState state = status.getState();
                    if (state == GHCommitState.SUCCESS) succeeded++;
                    else if (state == GHCommitState.PENDING) pending++;
                    else failing++;
                }
            }
            return new GitHubPullRequestState(open, merged, pr.isDraft(), pr.getMergeable(),
                    pr.getMergeableState(), sha, pr.getBase().getRef(), pending, failing, succeeded);
        } catch (IOException | GHException | UncheckedIOException e) {
            throw translate(e);
        }
    }

    private static boolean isNewer(GHCommitStatus candidate, GHCommitStatus current) {
        try {
            var a = candidate.getCreatedAt();
            var b = current.getCreatedAt();
            return a != null && b != null && a.after(b);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public void squashMergePullRequest(GitHubPullRequestRef ref, String expectedHeadSha, String commitMessage,
                                       String apiUrl, String token) throws GitHubApiException {
        if (expectedHeadSha == null || expectedHeadSha.isBlank()) {
            throw new IllegalArgumentException("expectedHeadSha is required");
        }
        try {
            GitHub github = client(apiUrl, token);
            GHPullRequest pr = github.getRepository(ref.owner() + "/" + ref.repo()).getPullRequest(ref.number());
            pr.merge(commitMessage, expectedHeadSha, GHPullRequest.MergeMethod.SQUASH);
        } catch (IOException | GHException | UncheckedIOException e) {
            throw translate(e);
        }
    }

    static GitHubApiException translate(Throwable failure) {
        Throwable t = failure;
        for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
            if (t instanceof RateLimitedException r) {
                return new GitHubApiException(r.status, true);
            }
        }
        t = failure;
        for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
            if (t instanceof GHFileNotFoundException) {
                return new GitHubApiException(404, false);
            }
            if (t instanceof HttpException http && http.getResponseCode() >= 400) {
                return new GitHubApiException(http.getResponseCode(), http.getResponseCode() == 429);
            }
        }
        return new GitHubApiException(failure);
    }
}
