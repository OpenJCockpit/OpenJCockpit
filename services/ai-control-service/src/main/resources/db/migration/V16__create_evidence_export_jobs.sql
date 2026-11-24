CREATE TABLE evidence_export_jobs (
    id               UUID NOT NULL PRIMARY KEY,
    project_id       UUID NOT NULL REFERENCES projects(id),
    scope            VARCHAR(20) NOT NULL,
    workflow_id      VARCHAR(128),
    format           VARCHAR(20) NOT NULL,
    include_outputs  BOOLEAN NOT NULL DEFAULT FALSE,
    from_at          TIMESTAMP WITH TIME ZONE,
    to_at            TIMESTAMP WITH TIME ZONE,
    status           VARCHAR(20) NOT NULL,
    runs_done        INTEGER NOT NULL DEFAULT 0,
    runs_total       INTEGER NOT NULL DEFAULT 0,
    filename         VARCHAR(255),
    size_bytes       BIGINT,
    file_path        VARCHAR(1000),
    error            VARCHAR(1000),
    created_by       VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_eej_project_status ON evidence_export_jobs(project_id, status);
CREATE INDEX idx_eej_status_updated ON evidence_export_jobs(status, updated_at);
