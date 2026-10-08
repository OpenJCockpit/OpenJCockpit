CREATE TABLE skills_marketplace_connections (
    id                UUID NOT NULL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    name_key          VARCHAR(100),
    marketplace_url   VARCHAR(500) NOT NULL,
    encrypted_api_key TEXT,
    description       VARCHAR(500),
    enabled           SMALLINT NOT NULL DEFAULT 1,
    active            SMALLINT NOT NULL DEFAULT 1,
    created_by        VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_skills_marketplace_name_key ON skills_marketplace_connections(name_key);
CREATE INDEX idx_skills_marketplace_active ON skills_marketplace_connections(active);
