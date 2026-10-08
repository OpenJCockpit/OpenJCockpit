package nl.metafactory.aicontrol.model;

public enum GitWorkspaceJobStatus {
    CREATED,
    CONTAINER_CREATED,
    CONTAINER_STARTED,
    SPEC_READY,
    CLONING,
    BRANCH_CREATED,
    AGENTS_RUNNING,
    CHANGES_DETECTED,
    NO_CHANGES,
    COMMITTING,
    PUSHING,
    PUSHED,
    CLEANING_UP,
    COMPLETED,
    FAILED,
    CANCELLED
}