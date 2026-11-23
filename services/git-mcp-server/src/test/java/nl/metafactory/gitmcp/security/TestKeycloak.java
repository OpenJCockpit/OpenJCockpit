package nl.metafactory.gitmcp.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stand-in for Keycloak in tests: an RSA signing key, its JWKS and OpenID configuration served over HTTP on a
 * random local port, and tokens signed with that key (or another one). No real Keycloak is involved.
 */
final class TestKeycloak implements AutoCloseable {

    static final String AUDIENCE = "openjcockpit-git-mcp";
    static final String LITELLM_CLIENT = "openjcockpit-litellm-git";
    static final String AGENT_SERVICE_CLIENT = "openjcockpit-agent-service";

    private final RSAKey signingKey = generateKey("test-key");
    private final RSAKey otherKey = generateKey("test-key");
    private final HttpServer server;
    private final String baseUrl;

    TestKeycloak() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        String discovery = "{\"issuer\":\"" + issuer() + "\",\"jwks_uri\":\"" + jwkSetUri() + "\","
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
        server.createContext("/realms/test/protocol/openid-connect/certs", exchange -> respond(exchange, jwks));
        server.createContext("/realms/test/.well-known/openid-configuration", exchange -> respond(exchange, discovery));
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    String issuer() {
        return baseUrl + "/realms/test";
    }

    String jwkSetUri() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    /** A token that passes every check, adjusted by {@code customizer}. */
    String token(Consumer<JWTClaimsSet.Builder> customizer) {
        return sign(signingKey, claims(customizer));
    }

    String validToken() {
        return token(claims -> { });
    }

    /** A valid-looking token signed with a key that is not in the JWKS (same key id). */
    String tokenSignedWithOtherKey() {
        return sign(otherKey, claims(claims -> { }));
    }

    /** The valid claims without any signature ({@code alg: none}). */
    String unsignedToken() {
        return new PlainJWT(claims(claims -> { })).serialize();
    }

    private JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        var builder = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .audience(List.of(AUDIENCE))
                .claim("azp", LITELLM_CLIENT)
                .subject("service-account-" + LITELLM_CLIENT)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)));
        customizer.accept(builder);
        return builder.build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
                    .type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Properties for the enabled mode, pointing at this stand-in. */
    McpSecurityProperties enabledProperties(boolean explicitJwkSetUri) {
        var properties = new McpSecurityProperties();
        properties.setKeycloakEnabled("true");
        properties.setIssuerUri(issuer());
        properties.setJwkSetUri(explicitJwkSetUri ? jwkSetUri() : "");
        properties.setExpectedAudience(AUDIENCE);
        properties.setAllowedAzp(LITELLM_CLIENT + ", " + AGENT_SERVICE_CLIENT);
        properties.afterPropertiesSet();
        return properties;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
