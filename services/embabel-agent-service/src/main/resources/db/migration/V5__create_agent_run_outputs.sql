-- Full text output of every catalog agent in a workflow run (run events keep only a short excerpt).
-- Read back by GET /api/agent-runs/{runId}/outputs, e.g. by git-workspace jobs that commit the
-- outputs to the job branch. agent_id is the agent reference key (local:<id> / a2a:<provider>:<id>).
CREATE TABLE agent_run_outputs (
    run_id       VARCHAR(64)  NOT NULL REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    agent_id     VARCHAR(160) NOT NULL,
    position     INTEGER      NOT NULL,
    output       TEXT         NOT NULL,
    recorded_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (run_id, agent_id)
);
