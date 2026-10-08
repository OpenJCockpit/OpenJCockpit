package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These defaults are the only source of truth for {@code metafactory.workflow-trigger} until a
 * later work package adds the corresponding {@code application.yml} keys (see
 * {@link WorkflowTriggerProperties}'s class Javadoc).
 */
class WorkflowTriggerPropertiesTest {

    @Test
    void defaultsMatchJavaInitialisers() {
        var properties = new WorkflowTriggerProperties();

        assertThat(properties.getSequentialMaxWait()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMaxChainDepth()).isEqualTo(3);
        assertThat(properties.getMaxOrbsPerWorkflow()).isEqualTo(5);
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void allFieldsCanBeSetAndRead() {
        var properties = new WorkflowTriggerProperties();

        properties.setSequentialMaxWait(Duration.ofMinutes(45));
        properties.setMaxChainDepth(5);
        properties.setMaxOrbsPerWorkflow(10);
        properties.setEnabled(false);

        assertThat(properties.getSequentialMaxWait()).isEqualTo(Duration.ofMinutes(45));
        assertThat(properties.getMaxChainDepth()).isEqualTo(5);
        assertThat(properties.getMaxOrbsPerWorkflow()).isEqualTo(10);
        assertThat(properties.isEnabled()).isFalse();
    }
}
