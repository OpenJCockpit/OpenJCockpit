package nl.metafactory.common.agentruntime;

import java.util.Objects;
import java.util.Set;

/**
 * Server-side, tenant-scoped grant for one agent (PostgreSQL {@code agent_tenant_bindings}).
 * {@code allowedTools} is the tool allowlist; {@code requiredGuardrails} are added to whatever the
 * definition requests. {@code modelAlias}/{@code pinnedVersion} are optional exact-match pins.
 */
public record AgentTenantBinding(
        String tenantId,
        String agentId,
        boolean enabled,
        String modelAlias,
        String pinnedVersion,
        boolean fallbackAllowed,
        boolean fallbackOnNotFound,
        Set<String> allowedTools,
        Set<String> requiredGuardrails
) {

    public AgentTenantBinding {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        allowedTools = Set.copyOf(allowedTools == null ? Set.of() : allowedTools);
        requiredGuardrails = Set.copyOf(requiredGuardrails == null ? Set.of() : requiredGuardrails);
    }
}
