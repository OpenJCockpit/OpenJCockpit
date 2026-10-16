package nl.metafactory.agents.definition.binding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.flywaydb.core.Flyway;

import static org.assertj.core.api.Assertions.assertThat;

/** The docker-compose-only seed binds the four delivery agents to tenant 'local', idempotently. */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed-local")
class LocalSeedBindingsTest {

    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @Test
    void seedsTheFourDeliveryAgentsForTenantLocal() {
        var repo = new AgentTenantBindingRepository(jdbc);
        assertThat(repo.enabledAgentIds("local"))
                .containsExactly("business-analyst", "qa-engineer", "software-developer", "solution-architect");
        var ba = repo.find("local", "business-analyst").orElseThrow();
        assertThat(ba.modelAlias()).isEqualTo("agent-chat");
        assertThat(ba.allowedTools()).containsExactlyInAnyOrder("git.list_files", "git.read_file");
        assertThat(repo.find("local", "software-developer").orElseThrow().allowedTools())
                .containsExactlyInAnyOrder("git.list_files", "git.read_file", "git.write_file");
        assertThat(ba.requiredGuardrails()).containsExactly("pii-protection");
    }

    @Test
    void reapplyingKeepsOperatorChanges() throws Exception {
        jdbc.sql("UPDATE agent_tenant_bindings SET enabled = FALSE WHERE agent_id = 'qa-engineer'").update();
        String seed = new String(getClass().getResourceAsStream("/db/seed-local/R__seed_local_agent_bindings.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        for (String statement : seed.replaceAll("(?m)^--.*$", "").split(";")) {
            if (!statement.isBlank()) {
                jdbc.sql(statement).update();
            }
        }
        assertThat(new AgentTenantBindingRepository(jdbc).enabledAgentIds("local")).doesNotContain("qa-engineer");
        assertThat(flyway.info().applied()).anyMatch(m -> m.getDescription().contains("seed local agent bindings"));
    }
}
