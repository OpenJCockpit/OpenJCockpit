CREATE TABLE spec_queues (
    project_id            UUID NOT NULL PRIMARY KEY,
    state                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    auto_merge_allowed    BOOLEAN NOT NULL DEFAULT FALSE,
    settings_updated_by   VARCHAR(80),
    settings_updated_at   TIMESTAMP WITH TIME ZONE,
    last_polled_at        TIMESTAMP WITH TIME ZONE,
    last_poll_error_code  VARCHAR(40),
    last_poll_error_at    TIMESTAMP WITH TIME ZONE,
    last_planner_decision VARCHAR(40),
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_spec_queues_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_spec_queues_state CHECK (
        CASE state WHEN 'ACTIVE' THEN TRUE WHEN 'PAUSED' THEN TRUE WHEN 'HALTED' THEN TRUE ELSE FALSE END)
);

CREATE TABLE spec_queue_items (
    id                       UUID NOT NULL PRIMARY KEY,
    project_id               UUID NOT NULL,
    spec_file                VARCHAR(500) NOT NULL,
    workflow_id              VARCHAR(200) NOT NULL,
    workflow_name            VARCHAR(255),
    auto_merge               BOOLEAN NOT NULL DEFAULT FALSE,
    position                 BIGINT NOT NULL,
    status                   VARCHAR(30) NOT NULL,
    failure_reason           VARCHAR(40),
    workflow_run_id          VARCHAR(100),
    last_run_status          VARCHAR(40),
    start_claimed_at         TIMESTAMP WITH TIME ZONE,
    merge_attempt_started_at TIMESTAMP WITH TIME ZONE,
    cancel_requested_at      TIMESTAMP WITH TIME ZONE,
    started_at               TIMESTAMP WITH TIME ZONE,
    finished_at              TIMESTAMP WITH TIME ZONE,
    merge_head_sha           VARCHAR(64),
    spec_file_size_bytes     INTEGER,
    open_spec_key            VARCHAR(500),
    active_slot              SMALLINT,
    created_by_sub           VARCHAR(80) NOT NULL,
    created_by_username      VARCHAR(255),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sqitem_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_sqitem_position UNIQUE (project_id, position),
    CONSTRAINT uq_sqitem_open_spec UNIQUE (project_id, open_spec_key),
    CONSTRAINT uq_sqitem_active_slot UNIQUE (project_id, active_slot),
    CONSTRAINT ck_sqitem_status_slot CHECK (
        CASE status
            WHEN 'QUEUED' THEN active_slot IS NULL
            WHEN 'STARTING' THEN active_slot IS NOT NULL AND active_slot = 1
            WHEN 'RUNNING' THEN active_slot IS NOT NULL AND active_slot = 1
            WHEN 'AWAITING_MERGE' THEN active_slot IS NOT NULL AND active_slot = 1
            WHEN 'MERGING' THEN active_slot IS NOT NULL AND active_slot = 1
            WHEN 'MERGED' THEN active_slot IS NULL
            WHEN 'COMPLETED_NO_CHANGES' THEN active_slot IS NULL
            WHEN 'FAILED' THEN active_slot IS NULL
            WHEN 'SKIPPED' THEN active_slot IS NULL
            WHEN 'CANCELLED' THEN active_slot IS NULL
            WHEN 'REMOVED' THEN active_slot IS NULL
            ELSE FALSE
        END)
);

CREATE INDEX idx_sqitem_status ON spec_queue_items (status);
CREATE INDEX idx_sqitem_project_status ON spec_queue_items (project_id, status);

CREATE TABLE spec_queue_item_pull_requests (
    id                 UUID NOT NULL PRIMARY KEY,
    item_id            UUID NOT NULL,
    workflow_run_id    VARCHAR(100) NOT NULL,
    url                VARCHAR(500) NOT NULL,
    owner              VARCHAR(100) NOT NULL,
    repo               VARCHAR(100) NOT NULL,
    number             INTEGER NOT NULL,
    merged_at          TIMESTAMP WITH TIME ZONE,
    closed_unmerged_at TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_sqpr_item FOREIGN KEY (item_id) REFERENCES spec_queue_items (id),
    CONSTRAINT uq_sqpr_item_run_url UNIQUE (item_id, workflow_run_id, url)
);

CREATE TABLE spec_queue_events (
    id                         UUID NOT NULL PRIMARY KEY,
    project_id                 UUID NOT NULL,
    item_id                    UUID,
    event_type                 VARCHAR(40) NOT NULL,
    actor                      VARCHAR(80) NOT NULL,
    from_status                VARCHAR(30),
    to_status                  VARCHAR(30),
    reason_code                VARCHAR(40),
    workflow_id                VARCHAR(200),
    workflow_run_id            VARCHAR(100),
    planner_decision           VARCHAR(40),
    planner_version            VARCHAR(40),
    planner_override           VARCHAR(40),
    run_duration_ms            BIGINT,
    review_iterations          INTEGER,
    approval_gate_iterations   INTEGER,
    approval_gate_outcome      VARCHAR(20),
    merge_result               VARCHAR(40),
    spec_file_size_bytes       INTEGER,
    pr_count                   INTEGER,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_sqevent_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_sqevent_item FOREIGN KEY (item_id) REFERENCES spec_queue_items (id)
);

CREATE INDEX idx_sqevent_project_created ON spec_queue_events (project_id, created_at);
CREATE INDEX idx_sqevent_item ON spec_queue_events (item_id);
