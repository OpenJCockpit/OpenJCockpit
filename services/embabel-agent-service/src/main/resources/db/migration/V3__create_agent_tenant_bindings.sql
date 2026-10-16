-- SPEC-002 (agent-definition-a2a): server-side binding of an agent definition to a tenant.
-- Approved for all databases by the owner (2026-10-14); part of the regular db/migration location.
-- The definition file can only narrow what is granted here, never widen it.
CREATE TABLE agent_tenant_bindings (
    tenant_id              VARCHAR(128) NOT NULL,
    agent_id               VARCHAR(64)  NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    -- optional pins: when set, the resolved definition must match exactly
    model_alias            VARCHAR(64),
    pinned_version         VARCHAR(64),
    -- per-agent fallback permission (remote -> last-known-good -> local)
    fallback_allowed       BOOLEAN      NOT NULL DEFAULT FALSE,
    fallback_on_not_found  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, agent_id)
);

CREATE TABLE agent_tenant_binding_tools (
    tenant_id  VARCHAR(128) NOT NULL,
    agent_id   VARCHAR(64)  NOT NULL,
    tool_id    VARCHAR(64)  NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, tool_id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES agent_tenant_bindings (tenant_id, agent_id) ON DELETE CASCADE
);

CREATE TABLE agent_tenant_binding_guardrails (
    tenant_id     VARCHAR(128) NOT NULL,
    agent_id      VARCHAR(64)  NOT NULL,
    guardrail_id  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, guardrail_id),
    FOREIGN KEY (tenant_id, agent_id) REFERENCES agent_tenant_bindings (tenant_id, agent_id) ON DELETE CASCADE
);
