-- Local development seed (Spring profile `local` adds classpath:db/seed-local to the Flyway locations).
-- Binds the seeded delivery agents to tenant 'local', the tenant_id carried by tokens of the
-- Keycloak service client 'openjcockpit-a2a' (infrastructure/local-seed/keycloak-a2a-client.sh).
-- Insert-if-missing only, so rows an operator changed are never overwritten. Portable SQL
-- (PostgreSQL and the H2 test database), hence NOT EXISTS instead of ON CONFLICT.

INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                                   fallback_allowed, fallback_on_not_found, updated_at)
SELECT 'local', 'business-analyst', TRUE, 'agent-chat', NULL, FALSE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_bindings WHERE tenant_id = 'local' AND agent_id = 'business-analyst');
INSERT INTO agent_tenant_binding_guardrails (tenant_id, agent_id, guardrail_id)
SELECT 'local', 'business-analyst', 'pii-protection'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_guardrails
                  WHERE tenant_id = 'local' AND agent_id = 'business-analyst' AND guardrail_id = 'pii-protection');

INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                                   fallback_allowed, fallback_on_not_found, updated_at)
SELECT 'local', 'solution-architect', TRUE, 'agent-chat', NULL, FALSE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_bindings WHERE tenant_id = 'local' AND agent_id = 'solution-architect');
INSERT INTO agent_tenant_binding_guardrails (tenant_id, agent_id, guardrail_id)
SELECT 'local', 'solution-architect', 'pii-protection'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_guardrails
                  WHERE tenant_id = 'local' AND agent_id = 'solution-architect' AND guardrail_id = 'pii-protection');

INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                                   fallback_allowed, fallback_on_not_found, updated_at)
SELECT 'local', 'software-developer', TRUE, 'agent-chat', NULL, FALSE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_bindings WHERE tenant_id = 'local' AND agent_id = 'software-developer');
INSERT INTO agent_tenant_binding_guardrails (tenant_id, agent_id, guardrail_id)
SELECT 'local', 'software-developer', 'pii-protection'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_guardrails
                  WHERE tenant_id = 'local' AND agent_id = 'software-developer' AND guardrail_id = 'pii-protection');

INSERT INTO agent_tenant_bindings (tenant_id, agent_id, enabled, model_alias, pinned_version,
                                   fallback_allowed, fallback_on_not_found, updated_at)
SELECT 'local', 'qa-engineer', TRUE, 'agent-chat', NULL, FALSE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_bindings WHERE tenant_id = 'local' AND agent_id = 'qa-engineer');
INSERT INTO agent_tenant_binding_guardrails (tenant_id, agent_id, guardrail_id)
SELECT 'local', 'qa-engineer', 'pii-protection'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_guardrails
                  WHERE tenant_id = 'local' AND agent_id = 'qa-engineer' AND guardrail_id = 'pii-protection');

-- Git tools of the seeded agents (the run's git workspace, via the LiteLLM git MCP server).
-- They only work when the workflow run is bound to a workspace (runs on a project); otherwise they report that.
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'business-analyst', 'git.list_files'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'business-analyst' AND tool_id = 'git.list_files');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'business-analyst', 'git.read_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'business-analyst' AND tool_id = 'git.read_file');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'solution-architect', 'git.list_files'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'solution-architect' AND tool_id = 'git.list_files');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'solution-architect', 'git.read_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'solution-architect' AND tool_id = 'git.read_file');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'software-developer', 'git.list_files'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'software-developer' AND tool_id = 'git.list_files');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'software-developer', 'git.read_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'software-developer' AND tool_id = 'git.read_file');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'software-developer', 'git.write_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'software-developer' AND tool_id = 'git.write_file');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'qa-engineer', 'git.list_files'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'qa-engineer' AND tool_id = 'git.list_files');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'qa-engineer', 'git.read_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'qa-engineer' AND tool_id = 'git.read_file');
INSERT INTO agent_tenant_binding_tools (tenant_id, agent_id, tool_id)
SELECT 'local', 'qa-engineer', 'git.write_file'
WHERE NOT EXISTS (SELECT 1 FROM agent_tenant_binding_tools
                  WHERE tenant_id = 'local' AND agent_id = 'qa-engineer' AND tool_id = 'git.write_file');
