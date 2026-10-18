package nl.metafactory.aicontrol.model;

public enum SpecQueueItemStatus {
    QUEUED,
    STARTING,
    RUNNING,
    AWAITING_MERGE,
    MERGING,
    MERGED,
    COMPLETED_NO_CHANGES,
    FAILED,
    SKIPPED,
    CANCELLED,
    REMOVED;

    /** Still occupies the queue. FAILED is deliberately not open: it blocks the queue until resolved. */
    public boolean isOpen() {
        return this == QUEUED || isActive();
    }

    /** Holds the project's single active slot. */
    public boolean isActive() {
        return this == STARTING || this == RUNNING || this == AWAITING_MERGE || this == MERGING;
    }

    /** Finished for good. FAILED is deliberately excluded (retry/skip/remove still possible). */
    public boolean isTerminal() {
        return this == MERGED || this == COMPLETED_NO_CHANGES || this == SKIPPED
                || this == CANCELLED || this == REMOVED;
    }
}
