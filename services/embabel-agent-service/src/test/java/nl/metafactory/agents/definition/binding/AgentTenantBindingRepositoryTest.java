package nl.metafactory.agents.definition.binding;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the SPEC-002 V3 migration on H2 in PostgreSQL mode and
 * proves that exactly one binding is read per authorised tenant and never across tenants.
 */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class AgentTenantBindingRepositoryTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    AgentTenantBindingRepository repository;

    @BeforeEach
    void seed() {
        repository = new AgentTenantBindingRepository(jdbc);
        insert("tenant-a", "customer-support", true, "customer-chat", true);
        insert("tenant-b", "customer-support", true, null, false);
        insert("tenant-a", "billing", false, null, false);
        jdbc.sql("INSERT INTO agent_tenant_binding_tools VALUES ('tenant-a','customer-support','customer.read')").update();
        jdbc.sql("INSERT INTO agent_tenant_binding_tools VALUES ('tenant-b','customer-support','customer.delete')").update();
        jdbc.sql("INSERT INTO agent_tenant_binding_guardrails VALUES ('tenant-a','customer-support','pii-protection')").update();
    }

    private void insert(String tenant, String agent, boolean enabled, String model, boolean fallback) {
        jdbc.sql("""
                INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                    fallback_allowed, fallback_on_not_found, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, FALSE, ?)""")
                .params(tenant, agent, enabled, model, fallback, OffsetDateTime.now()).update();
    }

    @Test
    void migrationIsRecordedExactlyOnce() {
        flyway.migrate();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = true AND \"version\" = '3'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void readsExactlyTheBindingOfTheAuthorisedTenant() {
        var binding = repository.find("tenant-a", "customer-support").orElseThrow();
        assertThat(binding.tenantId()).isEqualTo("tenant-a");
        assertThat(binding.modelAlias()).isEqualTo("customer-chat");
        assertThat(binding.fallbackAllowed()).isTrue();
        assertThat(binding.allowedTools()).containsExactly("customer.read");
        assertThat(binding.requiredGuardrails()).containsExactly("pii-protection");
    }

    @Test
    void neverLeaksAnotherTenantsGrants() {
        var b = repository.find("tenant-b", "customer-support").orElseThrow();
        assertThat(b.allowedTools()).containsExactly("customer.delete").doesNotContain("customer.read");
        assertThat(repository.find("tenant-c", "customer-support")).isEmpty();
    }

    @Test
    void enabledAgentIdsExcludeDisabledBindings() {
        assertThat(repository.enabledAgentIds("tenant-a")).containsExactly("customer-support");
        assertThat(repository.find("tenant-a", "billing").orElseThrow().enabled()).isFalse();
    }

    @Test
    void primaryKeyPreventsDuplicateBindings() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> insert("tenant-a", "customer-support", true, null, false))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
