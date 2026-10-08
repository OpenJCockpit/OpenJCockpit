CREATE TABLE project_git_credentials (
    id                               UUID NOT NULL PRIMARY KEY,
    project_id                       UUID NOT NULL REFERENCES projects(id),
    credential_type                  VARCHAR(30) NOT NULL DEFAULT 'NONE',
    username                         VARCHAR(255),
    encrypted_secret                 TEXT,
    encrypted_private_key_passphrase TEXT,
    github_api_url                   VARCHAR(500),
    active                           SMALLINT NOT NULL DEFAULT 1,
    last_used_at                     TIMESTAMP WITH TIME ZONE,
    created_at                       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_git_creds_project ON project_git_credentials(project_id);