package nl.metafactory.agents.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frozen Contract #2: every field default here must match the shipped
 * {@code application.yml}'s {@code ${VAR:default}} value, so a truncated or absent
 * {@code metafactory.approval-gate} YAML block cannot produce a permissive value.
 */
class ApprovalGatePropertiesTest {

    @Test
    void defaultsMatchFrozenContractTwo() {
        var properties = new ApprovalGateProperties();

        assertThat(properties.getMaxFeedbackIterations()).isEqualTo(3);
        assertThat(properties.getCommentMaxLength()).isEqualTo(4000);
        assertThat(properties.getMaxChangedPathsInContext()).isEqualTo(50);
    }

    @Test
    void allFieldsCanBeSetAndRead() {
        var properties = new ApprovalGateProperties();

        properties.setMaxFeedbackIterations(5);
        properties.setCommentMaxLength(8000);
        properties.setMaxChangedPathsInContext(100);

        assertThat(properties.getMaxFeedbackIterations()).isEqualTo(5);
        assertThat(properties.getCommentMaxLength()).isEqualTo(8000);
        assertThat(properties.getMaxChangedPathsInContext()).isEqualTo(100);
    }
}
