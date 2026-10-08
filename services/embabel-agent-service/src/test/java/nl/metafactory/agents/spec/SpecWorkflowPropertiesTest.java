package nl.metafactory.agents.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecWorkflowPropertiesTest {

    @Test
    void basePathDefaultsToCurrentDirectoryAndCanBeChanged() {
        var properties = new SpecWorkflowProperties();

        assertThat(properties.getBasePath()).isEqualTo(".");

        properties.setBasePath("/srv/project");
        assertThat(properties.getBasePath()).isEqualTo("/srv/project");
    }

    @Test
    void initEnabledDefaultsToTrueAndCanBeChanged() {
        var properties = new SpecWorkflowProperties();

        assertThat(properties.isInitEnabled()).isTrue();

        properties.setInitEnabled(false);
        assertThat(properties.isInitEnabled()).isFalse();
    }
}
