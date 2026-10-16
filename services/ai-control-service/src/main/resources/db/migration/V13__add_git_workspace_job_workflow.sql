-- Git-workspace jobs run the linked catalog agents of a workflow (embabel-agent-service). Nullable:
-- jobs created before this change ran the built-in pipeline and have no workflow.
ALTER TABLE git_workspace_jobs ADD COLUMN workflow_id VARCHAR(128);
