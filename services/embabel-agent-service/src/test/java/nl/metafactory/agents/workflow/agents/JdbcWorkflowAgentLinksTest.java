package nl.metafactory.agents.workflow.agents;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V4 linking table: ordered agent references per workflow (H2 in PostgreSQL mode). */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class JdbcWorkflowAgentLinksTest {

    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    JdbcWorkflowAgentLinks links;

    @BeforeEach
    void setUp() {
        links = new JdbcWorkflowAgentLinks(jdbc);
    }

    @Test
    void migrationIsAppliedOnce() {
        flyway.migrate();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = true AND \"version\" = '4'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void storesReferencesInOrderAndReplacesThem() {
        links.replace("wf-1", List.of("a2a:litellm:business-analyst", "local:triage", "a2a:litellm:qa-engineer"));
        assertThat(links.find("wf-1")).containsExactly("a2a:litellm:business-analyst", "local:triage", "a2a:litellm:qa-engineer");
        assertThat(jdbc.sql("SELECT source || '/' || COALESCE(provider, '-') || '/' || agent_id FROM workflow_agent_refs "
                + "WHERE workflow_id = 'wf-1' ORDER BY position").query(String.class).list())
                .containsExactly("A2A/litellm/business-analyst", "LOCAL/-/triage", "A2A/litellm/qa-engineer");

        links.replace("wf-1", List.of("local:triage"));
        assertThat(links.find("wf-1")).containsExactly("local:triage");
        assertThat(links.find("wf-other")).isEmpty();
        assertThat(links.find(null)).isEmpty();
    }

    @Test
    void deleteRemovesOnlyThatWorkflow() {
        links.replace("wf-1", List.of("local:a"));
        links.replace("wf-2", List.of("local:b"));
        links.delete("wf-1");
        assertThat(links.find("wf-1")).isEmpty();
        assertThat(links.find("wf-2")).containsExactly("local:b");
    }

    @Test
    void rejectsNonReferencesAndInconsistentRows() {
        assertThatThrownBy(() -> links.replace("wf-1", List.of("requirement"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO workflow_agent_refs VALUES ('wf-x', 0, 'LOCAL', 'litellm', 'a')").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
