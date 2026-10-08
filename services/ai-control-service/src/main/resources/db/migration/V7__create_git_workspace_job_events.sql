CREATE TABLE git_workspace_job_events (
    id          UUID NOT NULL PRIMARY KEY,
    job_id      UUID NOT NULL REFERENCES git_workspace_jobs(id),
    event_type  VARCHAR(100) NOT NULL,
    message     VARCHAR(2000),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_gwje_job ON git_workspace_job_events(job_id);
