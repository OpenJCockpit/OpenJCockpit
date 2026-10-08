package nl.metafactory.aicontrol.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Declares the single outbound HTTP client used for marketplace egress (ADR-3). This is
 * deliberately {@code java.net.http.HttpClient}, not {@code WebClient} or {@code RestClient}:
 * {@code BearerTokenRelayFilter} is a WebClient-only {@code ExchangeFilterFunction} and cannot be
 * attached to this type, so BR-9(a)'s "silent by-type bean capture" is a compile-time
 * impossibility rather than a discipline. {@code embabelWebClient} remains the module's only
 * {@code WebClient} bean (asserted by {@code SkillsMarketplaceEgressWiringTest}).
 *
 * <p>No cookie handler, no authenticator, and — per ADR-5 — no {@code Executor}/{@code
 * ExecutorService} bean is declared anywhere for this feature; the executor below is a private,
 * unregistered builder argument, never a Spring bean, so it cannot demote the existing {@code
 * workflowExecutor} as the default {@code @Async} executor.</p>
 */
@Configuration
public class SkillsMarketplaceEgressConfig {

    @Bean
    HttpClient skillsMarketplaceHttpClient(SkillsMarketplaceProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }
}
