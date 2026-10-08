CREATE TABLE git_workspace_jobs (
    id                  UUID NOT NULL PRIMARY KEY,
    project_id          UUID NOT NULL REFERENCES projects(id),
    spec_file_ref       VARCHAR(500) NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    base_branch         VARCHAR(100),
    working_branch      VARCHAR(255),
    commit_hash         VARCHAR(64),
    container_id        VARCHAR(128),
    workspace_path      VARCHAR(500),
    error_code          VARCHAR(50),
    error_message       VARCHAR(1000),
    started_by          VARCHAR(255),
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_gwj_project ON git_workspace_jobs(project_id);
CREATE INDEX idx_gwj_status  ON git_workspace_jobs(status);
