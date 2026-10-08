package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    private List<String> authorities(Map<String, Object> claims) {
        return converter.convert(jwt(claims)).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void unionsScopesAndRealmRolesVerbatim() {
        assertThat(authorities(Map.of("scope", "openid profile",
                "realm_access", Map.of("roles", List.of("openjcockpit-admin", "Other")))))
                .containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile", "ROLE_openjcockpit-admin", "ROLE_Other");
    }

    @Test
    void ignoresMalformedClaimsAndResourceAccess() {
        assertThat(authorities(Map.of())).isEmpty();
        assertThat(authorities(Map.of("realm_access", "nope"))).isEmpty();
        assertThat(authorities(Map.of("realm_access", Map.of("roles", "admin")))).isEmpty();
        assertThat(authorities(Map.of("realm_access", Map.of("roles", Arrays.asList("a", 7, null)))))
                .containsExactly("ROLE_a");
        assertThat(authorities(Map.of("resource_access", Map.of("c", Map.of("roles", List.of("x")))))).isEmpty();
    }

    @Test
    void principalNameIsTheSubject() {
        assertThat(converter.convert(jwt(Map.of())).getName()).isEqualTo("sub-1");
    }
}
