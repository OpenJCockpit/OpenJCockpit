CREATE TABLE projects (
    id             UUID         NOT NULL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    customer_id    VARCHAR(100),
    git_url        VARCHAR(500),
    description    TEXT,
    default_branch VARCHAR(100) DEFAULT 'main',
    environment    VARCHAR(50),
    owner          VARCHAR(255),
    active         SMALLINT     NOT NULL DEFAULT 1,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_active ON projects(active);
CREATE INDEX idx_projects_name   ON projects(name);
