-- A2A services (LiteLLM-compatible gateways) added in the dashboard (Workflow designer -> Configuration).
-- Services from configuration (openjcockpit.a2a-providers) are not stored here. Secrets are stored
-- encrypted (A2A_SERVICE_ENCRYPTION_SECRET/SALT) and never returned by the API. id is the provider id
-- in agent references (a2a:<id>:<agent>), so it cannot be changed.
CREATE TABLE a2a_services (
    id                          VARCHAR(40)   PRIMARY KEY,
    type                        VARCHAR(16)   NOT NULL,
    name                        VARCHAR(100)  NOT NULL,
    base_url                    VARCHAR(2048) NOT NULL,
    public_url                  VARCHAR(2048),
    api_key_encrypted           TEXT          NOT NULL,
    caller_token_url            VARCHAR(2048),
    caller_client_id            VARCHAR(255),
    caller_client_secret_encrypted TEXT,
    created_by                  VARCHAR(255)  NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by                  VARCHAR(255)  NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT a2a_services_type CHECK (type = 'litellm'),
    CONSTRAINT a2a_services_caller_auth CHECK (
        (caller_token_url IS NULL AND caller_client_id IS NULL AND caller_client_secret_encrypted IS NULL)
        OR (caller_token_url IS NOT NULL AND caller_client_id IS NOT NULL AND caller_client_secret_encrypted IS NOT NULL))
);
