-- Ties a git-workspace job to its embabel agent run, so a project's execution evidence can be listed per run.
-- Nullable: jobs that never started a run (and the built-in pipeline) have none.
ALTER TABLE git_workspace_jobs ADD COLUMN run_id VARCHAR(128);

-- Backfill: finishWorkflowRunAsync recorded the run id only in the message of its
-- "Workflow run started: <runId>" event (the prefix is 22 characters). The latest such event wins.
UPDATE git_workspace_jobs
SET run_id = (SELECT SUBSTRING(e.message, 23)
              FROM git_workspace_job_events e
              WHERE e.job_id = git_workspace_jobs.id
                AND e.message LIKE 'Workflow run started: %'
              ORDER BY e.created_at DESC
              LIMIT 1)
WHERE EXISTS (SELECT 1
              FROM git_workspace_job_events e
              WHERE e.job_id = git_workspace_jobs.id
                AND e.message LIKE 'Workflow run started: %');

CREATE INDEX idx_gwj_project_created ON git_workspace_jobs(project_id, created_at DESC);
