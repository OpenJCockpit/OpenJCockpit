CREATE TABLE project_hermes_config (
    id                UUID NOT NULL PRIMARY KEY,
    project_id        UUID NOT NULL REFERENCES projects(id),
    enabled           SMALLINT NOT NULL DEFAULT 0,
    endpoint_url      VARCHAR(500),
    encrypted_auth_token TEXT,
    signal_type       VARCHAR(255),
    workflow_id       VARCHAR(255),
    active            SMALLINT NOT NULL DEFAULT 1,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_hermes_config_project ON project_hermes_config(project_id);
