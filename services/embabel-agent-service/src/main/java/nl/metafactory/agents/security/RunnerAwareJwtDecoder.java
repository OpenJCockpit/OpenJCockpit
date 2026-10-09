package nl.metafactory.agents.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Routes runner-shaped tokens (HS256 with the runner issuer) to a dedicated shared-secret decoder and
 * everything else to the Keycloak decoder, unchanged. Only one of the two paths can ever validate a token.
 */
public class RunnerAwareJwtDecoder implements JwtDecoder {

    private final JwtDecoder primary;
    private final String issuer;
    private final NimbusJwtDecoder runnerDecoder;

    public RunnerAwareJwtDecoder(JwtDecoder primary, SpecQueueRunnerTokenProperties props) {
        this(primary, props, Clock.systemUTC());
    }

    RunnerAwareJwtDecoder(JwtDecoder primary, SpecQueueRunnerTokenProperties props, Clock clock) {
        this.primary = primary;
        this.issuer = props.getIssuer();
        Duration skew = Duration.ofSeconds(props.getClockSkewSeconds());
        Duration maxLifetime = Duration.ofSeconds(props.getMaxLifetimeSeconds());
        this.runnerDecoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(props.secretBytes(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.runnerDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(skew),
                new JwtIssuerValidator(props.getIssuer()),
                audienceValidator(props.getAudience()),
                lifetimeValidator(maxLifetime, skew, clock)));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JWT parsed;
        try {
            parsed = JWTParser.parse(token);
        } catch (ParseException | RuntimeException e) {
            return primary.decode(token);
        }
        if (!isRunnerShaped(parsed)) {
            return primary.decode(token);
        }
        try {
            if (parsed.getJWTClaimsSet().getIssueTime() == null) {
                throw new BadJwtException("Runner token without iat");
            }
        } catch (ParseException e) {
            throw new BadJwtException("Malformed runner token");
        }
        return runnerDecoder.decode(token);
    }

    private boolean isRunnerShaped(JWT parsed) {
        try {
            return JWSAlgorithm.HS256.equals(parsed.getHeader().getAlgorithm())
                    && issuer.equals(parsed.getJWTClaimsSet().getIssuer());
        } catch (ParseException e) {
            return false;
        }
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
    }

    private static OAuth2TokenValidator<Jwt> lifetimeValidator(Duration max, Duration skew, Clock clock) {
        return jwt -> {
            Instant iat = jwt.getIssuedAt();
            Instant exp = jwt.getExpiresAt();
            OAuth2TokenValidatorResult bad = OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid token lifetime", null));
            if (iat == null || exp == null) return bad;
            if (Duration.between(iat, exp).compareTo(max) > 0) return bad;
            // A forged iat must not extend validity.
            if (Duration.between(clock.instant(), exp).compareTo(max.plus(skew)) > 0) return bad;
            return OAuth2TokenValidatorResult.success();
        };
    }
}
