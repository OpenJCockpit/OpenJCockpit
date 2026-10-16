-- SPEC-002 example binding for the customer-support sample agent. Run against a LOCAL database only.
-- The four delivery agents are seeded automatically in docker-compose (db/seed-local).
INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                                   fallback_allowed, fallback_on_not_found, updated_at)
VALUES ('demo-tenant', 'customer-support', TRUE, 'customer-chat', NULL, TRUE, FALSE, now());
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
VALUES ('demo-tenant', 'customer-support', 'customer.read');
INSERT INTO agent_tenant_binding_guardrails (tenant_id, agent_id, guardrail_id)
VALUES ('demo-tenant', 'customer-support', 'pii-protection');
