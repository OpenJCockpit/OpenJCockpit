package nl.metafactory.aicontrol.config;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerContainerHardeningTest {

    private static HostConfig hostConfig(AgenticWorkflowProperties.Container limits) {
        return DockerContainerRuntimeConfig.DockerContainerRuntime.hardenedHostConfig(limits, 128, 256L * 1024 * 1024);
    }

    @Test
    void jobContainersRunWithLeastPrivilegeAndBoundedResources() {
        HostConfig config = hostConfig(new AgenticWorkflowProperties.Container());

        assertThat(config.getPrivileged()).isFalse();
        assertThat(config.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(config.getCapAdd()).isNullOrEmpty();
        assertThat(config.getSecurityOpts()).containsExactly("no-new-privileges:true");
        assertThat(config.getReadonlyRootfs()).isTrue();
        assertThat(config.getBinds()).isNullOrEmpty();
        assertThat(config.getMounts()).isNullOrEmpty();
        assertThat(config.getTmpFs()).containsOnlyKeys("/workspace", "/tmp")
                .allSatisfy((path, options) -> assertThat(options).contains("noexec", "nosuid", "nodev", "size="));
        assertThat(config.getMemory()).isEqualTo(256L * 1024 * 1024);
        assertThat(config.getMemorySwap()).isEqualTo(config.getMemory());
        assertThat(config.getNanoCPUs()).isEqualTo(1_000_000_000L);
        assertThat(config.getPidsLimit()).isEqualTo(256L);
        assertThat(config.getNetworkMode()).isEqualTo("none");
    }

    @Test
    void aDedicatedNetworkCanBeConfigured() {
        var limits = new AgenticWorkflowProperties.Container();
        limits.setNetworkMode("openjcockpit-jobs");
        assertThat(hostConfig(limits).getNetworkMode()).isEqualTo("openjcockpit-jobs");
    }

    @Test
    void sharingTheHostOrAnotherContainersNetworkIsRefused() {
        for (String mode : new String[]{"host", "container:openjcockpit-postgres"}) {
            var limits = new AgenticWorkflowProperties.Container();
            limits.setNetworkMode(mode);
            assertThatThrownBy(() -> hostConfig(limits)).isInstanceOf(IllegalStateException.class);
        }
    }
}
