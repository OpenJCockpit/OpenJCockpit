-- Workflows reference agents of the central agent catalog (Workflow design -> Agents) instead of
-- built-in pipeline stages. One row per referenced agent, in execution order.
--   source   LOCAL  -> agent_id is an agent in the repository /agents folder (provider NULL)
--            A2A    -> agent_id is an agent name at A2A provider `provider` (e.g. litellm)
-- workflow_id refers to a workflow definition in the YAML definition store (not a table), hence
-- no foreign key; deleting a workflow deletes its rows (WorkflowDefinitionRepository).
CREATE TABLE workflow_agent_refs (
    workflow_id  VARCHAR(128) NOT NULL,
    position     INTEGER      NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    provider     VARCHAR(64),
    agent_id     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (workflow_id, position),
    CONSTRAINT workflow_agent_refs_source_provider CHECK (
        (source = 'LOCAL' AND provider IS NULL) OR (source = 'A2A' AND provider IS NOT NULL))
);
CREATE INDEX idx_workflow_agent_refs_agent ON workflow_agent_refs (source, provider, agent_id);
