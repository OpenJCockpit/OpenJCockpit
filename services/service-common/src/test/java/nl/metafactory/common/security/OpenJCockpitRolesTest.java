package nl.metafactory.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenJCockpitRolesTest {

    private final RoleHierarchy hierarchy = OpenJCockpitRoles.hierarchy();

    private List<String> reachable(String... roles) {
        List<GrantedAuthority> granted = java.util.Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(OpenJCockpitRoles.authority(r))).toList();
        return hierarchy.getReachableGrantedAuthorities(granted).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void roleNamesAreTheKeycloakRealmRoleNames() {
        assertThat(OpenJCockpitRoles.ALL).containsExactly(
                "openjcockpit-admin", "openjcockpit-operator", "openjcockpit-approver", "openjcockpit-viewer");
        assertThat(OpenJCockpitRoles.authority(OpenJCockpitRoles.ADMIN)).isEqualTo("ROLE_openjcockpit-admin");
    }

    @Test
    void adminImpliesEveryRole() {
        assertThat(reachable(OpenJCockpitRoles.ADMIN)).containsExactlyInAnyOrder(
                "ROLE_openjcockpit-admin", "ROLE_openjcockpit-operator", "ROLE_openjcockpit-approver",
                "ROLE_openjcockpit-viewer");
    }

    @Test
    void operatorAndApproverEachImplyOnlyViewer() {
        assertThat(reachable(OpenJCockpitRoles.OPERATOR))
                .containsExactlyInAnyOrder("ROLE_openjcockpit-operator", "ROLE_openjcockpit-viewer");
        assertThat(reachable(OpenJCockpitRoles.APPROVER))
                .containsExactlyInAnyOrder("ROLE_openjcockpit-approver", "ROLE_openjcockpit-viewer");
    }

    @Test
    void viewerAndUnknownRolesImplyNothingMore() {
        assertThat(reachable(OpenJCockpitRoles.VIEWER)).containsExactly("ROLE_openjcockpit-viewer");
        assertThat(reachable("offline_access")).containsExactly("ROLE_offline_access");
    }
}
