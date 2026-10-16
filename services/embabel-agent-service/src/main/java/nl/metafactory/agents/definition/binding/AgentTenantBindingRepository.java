package nl.metafactory.agents.definition.binding;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Reads tenant bindings with plain JDBC (no JPA entity, so {@code ddl-auto: validate} is unaffected
 * while the migration is not yet approved for real databases). Every query is scoped by both
 * tenant and agent id; there is no "find by agent" across tenants.
 */
public class AgentTenantBindingRepository {

    private final JdbcClient jdbc;

    public AgentTenantBindingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AgentTenantBinding> find(String tenantId, String agentId) {
        record Row(String tenantId, String agentId, boolean enabled, String modelAlias, String pinnedVersion,
                   boolean fallbackAllowed, boolean fallbackOnNotFound) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT tenant_id, agent_id, enabled, model_alias, pinned_version,
                               fallback_allowed, fallback_on_not_found
                        FROM agent_tenant_bindings WHERE tenant_id = :tenant AND agent_id = :agent""")
                .param("tenant", tenantId)
                .param("agent", agentId)
                .query((rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6), rs.getBoolean(7)))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new IllegalStateException("more than one binding for one tenant/agent pair");
        }
        var row = rows.getFirst();
        var tools = jdbc.sql("SELECT tool_id FROM agent_tenant_binding_tools WHERE tenant_id = :tenant AND agent_id = :agent")
                .param("tenant", tenantId).param("agent", agentId).query(String.class).list();
        var guardrails = jdbc.sql("SELECT guardrail_id FROM agent_tenant_binding_guardrails WHERE tenant_id = :tenant AND agent_id = :agent")
                .param("tenant", tenantId).param("agent", agentId).query(String.class).list();
        return Optional.of(new AgentTenantBinding(row.tenantId(), row.agentId(), row.enabled(), row.modelAlias(),
                row.pinnedVersion(), row.fallbackAllowed(), row.fallbackOnNotFound(), new HashSet<>(tools),
                new HashSet<>(guardrails)));
    }

    /** Agent ids the tenant has an enabled binding for (used for the Agent Card). */
    public List<String> enabledAgentIds(String tenantId) {
        return jdbc.sql("SELECT agent_id FROM agent_tenant_bindings WHERE tenant_id = :tenant AND enabled = TRUE ORDER BY agent_id")
                .param("tenant", tenantId).query(String.class).list();
    }
}
