package nl.metafactory.aicontrol.specqueue.runner;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Mints a fresh short-lived HS256 service token per call. */
@Component
public class RunnerTokenIssuer {

    public static final String RUNNER_SUBJECT = "spec-queue-runner";

    private final SpecQueueProperties.Token config;
    private final Clock clock;

    @Autowired
    public RunnerTokenIssuer(SpecQueueProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public RunnerTokenIssuer(SpecQueueProperties properties, Clock clock) {
        this.config = properties.getRunner().getToken();
        this.clock = clock;
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String issueToken() {
        if (!config.isConfigured()) {
            throw new RunnerIdentityUnconfiguredException();
        }
        Instant iat = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(config.getIssuer())
                .audience(List.of(config.getAudience()))
                .subject(RUNNER_SUBJECT)
                .claim("preferred_username", RUNNER_SUBJECT)
                .claim("scope", RUNNER_SUBJECT)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(iat))
                .expirationTime(Date.from(iat.plus(config.getTtl())))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(new SecretKeySpec(
                    config.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Runner token could not be signed");
        }
    }
}
