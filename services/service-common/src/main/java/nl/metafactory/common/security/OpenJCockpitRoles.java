package nl.metafactory.common.security;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.List;

/**
 * The OpenJCockpit role model: Keycloak realm roles, mapped to {@code ROLE_<name>} authorities by
 * {@link KeycloakRealmRoleConverter}. Role names are matched case-sensitively.
 *
 * <p>The implications between roles live here, in {@link #hierarchy()}, and nowhere else (Keycloak
 * assigns only the direct roles; no composite roles are used):
 * <pre>
 *   openjcockpit-admin    &gt; openjcockpit-operator, openjcockpit-approver
 *   openjcockpit-operator &gt; openjcockpit-viewer
 *   openjcockpit-approver &gt; openjcockpit-viewer
 * </pre>
 * Admin therefore implies every role; operator and approver are independent of each other, and both
 * imply read-only viewer access. A token without any of these roles is authorised for nothing.
 */
public final class OpenJCockpitRoles {

    /** Everything, including project, git-credential, OPA, integration, marketplace and A2A configuration. */
    public static final String ADMIN = "openjcockpit-admin";

    /** Starts/stops workflow runs and manages workflows, definitions, specs and spec queues. */
    public static final String OPERATOR = "openjcockpit-operator";

    /** Decides (approves/rejects) workflow approval gates. */
    public static final String APPROVER = "openjcockpit-approver";

    /** Read-only access (GET). */
    public static final String VIEWER = "openjcockpit-viewer";

    /** All roles, most privileged first. */
    public static final List<String> ALL = List.of(ADMIN, OPERATOR, APPROVER, VIEWER);

    private static final String ROLE_PREFIX = "ROLE_";

    private OpenJCockpitRoles() {
    }

    /** The Spring Security authority for a role name, e.g. {@code ROLE_openjcockpit-admin}. */
    public static String authority(String role) {
        return ROLE_PREFIX + role;
    }

    /** The single definition of which role implies which. */
    public static RoleHierarchy hierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN).implies(OPERATOR, APPROVER)
                .role(OPERATOR).implies(VIEWER)
                .role(APPROVER).implies(VIEWER)
                .build();
    }
}
