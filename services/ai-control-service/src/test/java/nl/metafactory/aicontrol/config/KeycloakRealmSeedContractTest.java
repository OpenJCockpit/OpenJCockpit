package nl.metafactory.aicontrol.config;

import nl.metafactory.common.security.OpenJCockpitRoles;
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

/** Guards the realm import file and seed script that the role model depends on. */
class KeycloakRealmSeedContractTest {

    private static final String ADMIN_ROLE = OpenJCockpitRoles.ADMIN;
    private static final String OPERATOR_ROLE = OpenJCockpitRoles.OPERATOR;
    private static final String APPROVER_ROLE = OpenJCockpitRoles.APPROVER;
    private static final String DEFAULT_ROLE = "default-roles-openjcockpit";

    private static JsonNode realm;
    private static String seedScript;

    @BeforeAll
    static void loadRealm() throws Exception {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("infrastructure/keycloak/openjcockpit-realm.json"))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("repository root containing infrastructure/keycloak").isNotNull();
        realm = JsonMapper.builder().build()
                .readTree(Files.readString(dir.resolve("infrastructure/keycloak/openjcockpit-realm.json")));
        seedScript = Files.readString(dir.resolve("infrastructure/keycloak/seed-realm-roles.sh"));
    }

    @Test
    void everyOpenJCockpitRoleIsDefinedAsARealmRole() {
        assertThat(names(realm.path("roles").path("realm"), "name"))
                .containsAll(OpenJCockpitRoles.ALL);
    }

    @Test
    void theSeedScriptCreatesEveryRoleForExistingDatabases() {
        for (String role : OpenJCockpitRoles.ALL.subList(1, OpenJCockpitRoles.ALL.size())) {
            assertThat(seedScript).as(role).contains("ensure_role " + role);
        }
        assertThat(seedScript).contains("KC_ADMIN_ROLE_NAME:-" + ADMIN_ROLE);
    }

    @Test
    void localDevUsersHoldTheirDocumentedRoles() {
        assertThat(realmRoles("tony")).contains(DEFAULT_ROLE, ADMIN_ROLE);
        assertThat(realmRoles("koen")).containsExactlyInAnyOrder(DEFAULT_ROLE, OPERATOR_ROLE, APPROVER_ROLE);
        assertThat(realmRoles("ricky")).containsExactlyInAnyOrder(DEFAULT_ROLE, OPERATOR_ROLE);
    }

    @Test
    void e2eUserCanRunAndApproveWorkflowsButIsNotAdmin() {
        // frontend/e2e creates, starts and deletes workflows and asserts a non-admin 403 on spec-queue settings.
        assertThat(realmRoles("e2e")).contains(OPERATOR_ROLE, APPROVER_ROLE).doesNotContain(ADMIN_ROLE);
        assertThat(realmRoles("koen")).doesNotContain(ADMIN_ROLE);
        assertThat(realmRoles("ricky")).doesNotContain(ADMIN_ROLE);
    }

    @Test
    void newUsersAreGrantedNoOpenJCockpitRoleByDefault() {
        assertThat(realm.has("defaultRoles")).isFalse();
        assertThat(realm.path("defaultRole").path("composites").toString()).doesNotContain("openjcockpit-");
        for (JsonNode role : realm.path("roles").path("realm")) {
            if (DEFAULT_ROLE.equals(role.path("name").asString())) {
                assertThat(role.path("composites").toString()).doesNotContain("openjcockpit-");
            }
        }
        assertThat(seedScript).contains("ensure_not_default");
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
