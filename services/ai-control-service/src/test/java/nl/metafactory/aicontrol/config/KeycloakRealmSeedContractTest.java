package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the realm import file that the spec-queue admin role depends on. */
class KeycloakRealmSeedContractTest {

    private static final String ADMIN_ROLE = "openjcockpit-admin";
    private static final String DEFAULT_ROLE = "default-roles-openjcockpit";

    private static JsonNode realm;

    @BeforeAll
    static void loadRealm() throws Exception {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("infrastructure/keycloak/openjcockpit-realm.json"))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("repository root containing infrastructure/keycloak").isNotNull();
        realm = JsonMapper.builder().build()
                .readTree(Files.readString(dir.resolve("infrastructure/keycloak/openjcockpit-realm.json")));
    }

    @Test
    void adminRealmRoleIsDefined() {
        assertThat(names(realm.path("roles").path("realm"), "name")).contains(ADMIN_ROLE);
    }

    @Test
    void tonyHoldsDefaultRoleAndAdminRole() {
        assertThat(realmRoles("tony")).contains(DEFAULT_ROLE, ADMIN_ROLE);
    }

    @Test
    void e2eKoenAndRickyDoNotHoldAdminRole() {
        assertThat(realmRoles("e2e")).doesNotContain(ADMIN_ROLE);
        assertThat(realmRoles("koen")).doesNotContain(ADMIN_ROLE);
        assertThat(realmRoles("ricky")).doesNotContain(ADMIN_ROLE);
    }

    private static List<String> realmRoles(String username) {
        for (JsonNode user : realm.path("users")) {
            if (username.equals(user.path("username").asString())) {
                return names(user.path("realmRoles"), null);
            }
        }
        throw new AssertionError("user not in realm: " + username);
    }

    private static List<String> names(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(field == null ? n.asString() : n.path(field).asString());
        }
        return out;
    }
}
