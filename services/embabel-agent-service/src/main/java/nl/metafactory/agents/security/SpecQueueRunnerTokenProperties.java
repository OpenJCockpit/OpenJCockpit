package nl.metafactory.agents.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Service identity of the spec-queue runner in ai-control-service. A blank secret disables the whole path. */
@Component
@ConfigurationProperties(prefix = "openjcockpit.security.spec-queue-runner")
public class SpecQueueRunnerTokenProperties {

    static final int MIN_SECRET_BYTES = 32;

    private String secret = "";
    private String issuer = "urn:openjcockpit:ai-control-service:spec-queue-runner";
    private String audience = "embabel-agent-service";
    private long maxLifetimeSeconds = 120;
    private long clockSkewSeconds = 30;

    @PostConstruct
    void validate() {
        if (isConfigured() && secretBytes().length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("spec-queue runner token secret must be at least "
                    + MIN_SECRET_BYTES + " bytes (UTF-8)");
        }
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    public byte[] secretBytes() {
        return secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public long getMaxLifetimeSeconds() { return maxLifetimeSeconds; }
    public void setMaxLifetimeSeconds(long maxLifetimeSeconds) { this.maxLifetimeSeconds = maxLifetimeSeconds; }
    public long getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(long clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }
}
