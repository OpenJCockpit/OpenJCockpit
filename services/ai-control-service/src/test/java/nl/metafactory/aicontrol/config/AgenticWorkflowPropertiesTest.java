package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticWorkflowPropertiesTest {

    @Test
    void preflightDockerTimeoutSecondsDefaultsAndCanBeChanged() {
        var properties = new AgenticWorkflowProperties();

        assertThat(properties.getPreflight().getDockerTimeoutSeconds()).isEqualTo(5);

        properties.getPreflight().setDockerTimeoutSeconds(15);
        assertThat(properties.getPreflight().getDockerTimeoutSeconds()).isEqualTo(15);
    }

    @Test
    void preflightGroupCanBeReplacedWholesale() {
        var properties = new AgenticWorkflowProperties();
        var replacement = new AgenticWorkflowProperties.Preflight();
        replacement.setDockerTimeoutSeconds(30);

        properties.setPreflight(replacement);

        assertThat(properties.getPreflight()).isSameAs(replacement);
        assertThat(properties.getPreflight().getDockerTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void containerNetworkModeDefaultsAndCanBeChanged() {
        var properties = new AgenticWorkflowProperties();

        assertThat(properties.getContainer().getNetworkMode()).isEqualTo("bridge");

        properties.getContainer().setNetworkMode("host");
        assertThat(properties.getContainer().getNetworkMode()).isEqualTo("host");
    }

    @Test
    void containerDockerHostDefaultsToBlankAndCanBeChanged() {
        var properties = new AgenticWorkflowProperties();

        assertThat(properties.getContainer().getDockerHost()).isEmpty();

        properties.getContainer().setDockerHost("tcp://host.docker.internal:2375");
        assertThat(properties.getContainer().getDockerHost()).isEqualTo("tcp://host.docker.internal:2375");
    }

    @Test
    void defaultBaseBranchDefaultsAndCanBeChanged() {
        var properties = new AgenticWorkflowProperties();

        assertThat(properties.getDefaultBaseBranch()).isEqualTo("main");

        properties.setDefaultBaseBranch("develop");
        assertThat(properties.getDefaultBaseBranch()).isEqualTo("develop");
    }
}
