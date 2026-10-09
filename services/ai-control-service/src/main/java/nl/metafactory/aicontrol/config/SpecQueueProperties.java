package nl.metafactory.aicontrol.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Spec-queue settings. Every default is also a field initialiser so a truncated or absent YAML
 * block cannot silently change behaviour.
 */
@Validated
@ConfigurationProperties(prefix = "openjcockpit.spec-queue")
public class SpecQueueProperties {

    @Valid
    @NotNull
    private Runner runner = new Runner();

    public Runner getRunner() { return runner; }
    public void setRunner(Runner runner) { this.runner = runner; }

    public static class Runner {
        private boolean enabled = true;

        @NotNull @DurationMin(seconds = 1)
        private Duration pollInterval = Duration.ofSeconds(20);
        @NotNull @DurationMin(seconds = 1)
        private Duration startLease = Duration.ofMinutes(5);
        @NotNull @DurationMin(seconds = 1)
        private Duration mergeLease = Duration.ofMinutes(5);
        @NotNull @DurationMin(seconds = 1)
        private Duration mergeWaitTimeout = Duration.ofMinutes(30);
        @NotNull @DurationMin(seconds = 1)
        private Duration embabelTimeout = Duration.ofSeconds(30);
        @NotNull @DurationMin(seconds = 1)
        private Duration githubTimeout = Duration.ofSeconds(30);

        @Positive
        private int recentlyFinishedLimit = 20;
        @Positive
        private int maxQueuedItems = 500;

        @Valid
        @NotNull
        private Token token = new Token();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
        public Duration getStartLease() { return startLease; }
        public void setStartLease(Duration startLease) { this.startLease = startLease; }
        public Duration getMergeLease() { return mergeLease; }
        public void setMergeLease(Duration mergeLease) { this.mergeLease = mergeLease; }
        public Duration getMergeWaitTimeout() { return mergeWaitTimeout; }
        public void setMergeWaitTimeout(Duration mergeWaitTimeout) { this.mergeWaitTimeout = mergeWaitTimeout; }
        public Duration getEmbabelTimeout() { return embabelTimeout; }
        public void setEmbabelTimeout(Duration embabelTimeout) { this.embabelTimeout = embabelTimeout; }
        public Duration getGithubTimeout() { return githubTimeout; }
        public void setGithubTimeout(Duration githubTimeout) { this.githubTimeout = githubTimeout; }
        public int getRecentlyFinishedLimit() { return recentlyFinishedLimit; }
        public void setRecentlyFinishedLimit(int recentlyFinishedLimit) { this.recentlyFinishedLimit = recentlyFinishedLimit; }
        public int getMaxQueuedItems() { return maxQueuedItems; }
        public void setMaxQueuedItems(int maxQueuedItems) { this.maxQueuedItems = maxQueuedItems; }
        public Token getToken() { return token; }
        public void setToken(Token token) { this.token = token; }
    }

    public static class Token {
        private static final int MIN_SECRET_BYTES = 32;

        private String secret = "";
        @NotBlank
        private String issuer = "urn:openjcockpit:ai-control-service:spec-queue-runner";
        @NotBlank
        private String audience = "embabel-agent-service";
        @NotNull @DurationMin(seconds = 1) @DurationMax(seconds = 120)
        private Duration ttl = Duration.ofSeconds(60);

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        public boolean isConfigured() {
            return secret != null && !secret.isBlank();
        }

        /** Blank/absent is fine (unconfigured); a non-blank secret must be >= 32 UTF-8 bytes. Never echoes the secret. */
        @AssertTrue(message = "spec-queue runner token secret must be at least 32 bytes (UTF-8)")
        public boolean isSecretLongEnough() {
            return !isConfigured() || secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
        }
    }
}
