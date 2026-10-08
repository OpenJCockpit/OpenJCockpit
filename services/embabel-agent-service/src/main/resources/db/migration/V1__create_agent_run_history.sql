CREATE TABLE agent_runs (
    run_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    workflow_id     VARCHAR(128),
    customer_id     VARCHAR(255),
    spec_file       TEXT,
    repository_url  VARCHAR(1000),
    status          VARCHAR(50)  NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    started_by      VARCHAR(255),
    reconciled_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_agent_runs_workflow ON agent_runs (workflow_id, started_at, run_id);
CREATE INDEX idx_agent_runs_status   ON agent_runs (status);

CREATE TABLE agent_run_events (
    run_id       VARCHAR(64)  NOT NULL REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    sequence_no  INTEGER      NOT NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_id     VARCHAR(128),
    title        VARCHAR(2000),
    status       VARCHAR(50),
    evidence_ref VARCHAR(1000),
    PRIMARY KEY (run_id, sequence_no)
);

CREATE TABLE agent_run_artifacts (
    run_id       VARCHAR(64)  NOT NULL REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    sequence_no  INTEGER      NOT NULL,
    artifact     VARCHAR(2000) NOT NULL,
    PRIMARY KEY (run_id, sequence_no)
);
