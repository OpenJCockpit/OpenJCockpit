package nl.metafactory.aicontrol.integration.http;

import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The <em>only</em> outbound path for marketplace egress (ADR-1, BR-14 "choke point"). Validates
 * the composed URI against {@link EgressGuard} before the first request and again after every
 * resolved redirect {@code Location} — never only on the originally stored URL (AC-29). Enforces
 * the response byte cap by streaming abort; the full body is never buffered beyond the cap.
 *
 * <p>{@code HttpRequest.Builder.timeout(...)} does not, in practice, bound the time spent
 * consuming a slow response body when using {@link HttpResponse.BodyHandlers#ofInputStream()}
 * (it only bounds the wait for the response to become available) — so body reading is performed
 * on a short-lived, per-call virtual-thread task with its own bounded {@code Future.get(...)},
 * closing the underlying stream and classifying the attempt as {@code TIMEOUT} if the absolute
 * per-request deadline is exceeded. This executor is created and shut down entirely within a
 * single method call; it is never registered as a Spring bean (ADR-5).</p>
 */
@Component
public class GuardedHttpGateway {

    private static final Logger log = LoggerFactory.getLogger(GuardedHttpGateway.class);
    private static final int READ_CHUNK_BYTES = 8192;

    private final HttpClient httpClient;
    private final SkillsMarketplaceProperties properties;
    private final EgressGuard egressGuard;

    public GuardedHttpGateway(HttpClient skillsMarketplaceHttpClient, SkillsMarketplaceProperties properties,
                               EgressGuard egressGuard) {
        this.httpClient = skillsMarketplaceHttpClient;
        this.properties = properties;
        this.egressGuard = egressGuard;
    }

    public OutboundHttpResult get(URI uri, Map<String, String> headers, String connectionId, String connectionName) {
        URI target = uri;
        int redirects = 0;
        Duration timeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());

        while (true) {
            Optional<String> denial = egressGuard.denialReason(target, connectionId, connectionName);
            if (denial.isPresent()) {
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.BLOCKED_BY_POLICY, null);
            }

            Instant deadline = Instant.now().plus(timeout);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(target)
                    .GET()
                    .timeout(timeout);
            headers.forEach(requestBuilder::header);

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException e) {
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TIMEOUT, null);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.UNREACHABLE, null);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                Optional<String> location = response.headers().firstValue("Location");
                closeQuietly(response.body());
                if (location.isEmpty()) {
                    return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.MALFORMED_TARGET, status);
                }
                if (redirects >= properties.getMaxRedirects()) {
                    log.warn("Skills marketplace egress redirect budget exhausted connectionId={} connectionName={} target={}",
                            connectionId, connectionName, EgressGuard.reducedTarget(target));
                    return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TOO_MANY_REDIRECTS, status);
                }
                URI resolved;
                try {
                    resolved = target.resolve(location.get());
                } catch (IllegalArgumentException e) {
                    return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.MALFORMED_TARGET, status);
                }
                target = resolved;
                redirects++;
                continue;
            }

            return readBoundedBodyWithDeadline(response, status, deadline);
        }
    }

    private OutboundHttpResult readBoundedBodyWithDeadline(HttpResponse<InputStream> response, int status, Instant deadline) {
        long maxBytes = properties.getMaxResponseBytes();

        // response.headers().firstValue("Content-Length") is only ever present here as a value
        // the JDK's own HTTP/1.1 framing layer has already parsed as a valid non-negative long in
        // order to construct this HttpResponse at all — a malformed value causes httpClient.send()
        // itself to fail before a response object exists, so no defensive parse-failure handling
        // is reachable or needed here.
        Optional<String> contentLengthHeader = response.headers().firstValue("Content-Length");
        if (contentLengthHeader.isPresent()) {
            long declared = Long.parseLong(contentLengthHeader.get());
            if (declared > maxBytes) {
                closeQuietly(response.body());
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.RESPONSE_TOO_LARGE, status);
            }
        }

        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<OutboundHttpResult> future = reader.submit(() -> readBoundedBody(response, status, maxBytes));
            long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
            try {
                return future.get(remainingMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                closeQuietly(response.body());
                future.cancel(true);
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.TIMEOUT, status);
            } catch (ExecutionException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.UNREACHABLE, status);
            }
        } finally {
            reader.shutdownNow();
        }
    }

    private OutboundHttpResult readBoundedBody(HttpResponse<InputStream> response, int status, long maxBytes) {
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        try (InputStream in = response.body()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_CHUNK_BYTES];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.RESPONSE_TOO_LARGE, status);
                }
                buffer.write(chunk, 0, read);
            }
            return new OutboundHttpResult.Response(status, contentType, buffer.toByteArray());
        } catch (IOException e) {
            return new OutboundHttpResult.Failure(OutboundHttpResult.FailureKind.UNREACHABLE, status);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
