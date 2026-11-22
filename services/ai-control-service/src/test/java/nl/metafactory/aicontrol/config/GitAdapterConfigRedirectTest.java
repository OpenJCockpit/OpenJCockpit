package nl.metafactory.aicontrol.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitAdapterConfigRedirectTest {

    @Test
    void connectivityCheckDoesNotFollowRedirects() throws Exception {
        var targetHits = new AtomicInteger();
        var target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", ex -> {
            targetHits.incrementAndGet();
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        target.start();
        var redirector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirector.createContext("/", ex -> {
            ex.getResponseHeaders().add("Location",
                    "http://127.0.0.1:" + target.getAddress().getPort() + ex.getRequestURI());
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        redirector.start();
        try {
            var port = new GitAdapterConfig().jGitPort();
            // http is rejected by the service layer, but the adapter itself must still not follow a redirect.
            assertThatThrownBy(() -> port.checkReachable(
                    "http://127.0.0.1:" + redirector.getAddress().getPort() + "/org/repo", null, 5))
                    .isInstanceOf(Exception.class);
            assertThat(targetHits).hasValue(0);
        } finally {
            redirector.stop(0);
            target.stop(0);
        }
    }
}
