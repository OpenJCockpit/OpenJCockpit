CREATE TABLE project_jira_config (
    id                   UUID NOT NULL PRIMARY KEY,
    project_id           UUID NOT NULL REFERENCES projects(id),
    enabled              SMALLINT NOT NULL DEFAULT 0,
    base_url             VARCHAR(500),
    project_key          VARCHAR(50),
    encrypted_auth_token TEXT,
    issue_type_mapping   VARCHAR(255),
    workflow_id          VARCHAR(255),
    active               SMALLINT NOT NULL DEFAULT 1,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jira_config_project ON project_jira_config(project_id);
