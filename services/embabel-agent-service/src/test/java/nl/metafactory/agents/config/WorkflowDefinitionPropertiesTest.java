package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionPropertiesTest {

    @Test
    void defaultPathPointsUnderTempDir() {
        var properties = new WorkflowDefinitionProperties();

        assertThat(properties.getPath()).endsWith("/embabel-workflow-definitions");
    }

    @Test
    void pathCanBeOverridden() {
        var properties = new WorkflowDefinitionProperties();

        properties.setPath("/custom/path");

        assertThat(properties.getPath()).isEqualTo("/custom/path");
    }
}
