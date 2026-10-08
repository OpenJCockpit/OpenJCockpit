CREATE TABLE project_document_folder_config (
    id                     UUID NOT NULL PRIMARY KEY,
    project_id             UUID NOT NULL REFERENCES projects(id),
    folder_path            VARCHAR(500),
    file_trigger_enabled   SMALLINT NOT NULL DEFAULT 0,
    allowed_document_types VARCHAR(500),
    workflow_id            VARCHAR(255),
    active                 SMALLINT NOT NULL DEFAULT 1,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_document_folder_config_project ON project_document_folder_config(project_id);
