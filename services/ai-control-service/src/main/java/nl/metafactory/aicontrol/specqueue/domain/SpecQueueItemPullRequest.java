package nl.metafactory.aicontrol.specqueue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spec_queue_item_pull_requests")
public class SpecQueueItemPullRequest {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "workflow_run_id", nullable = false, updatable = false, length = 100)
    private String workflowRunId;

    @Column(nullable = false, updatable = false, length = 500)
    private String url;

    @Column(nullable = false, updatable = false, length = 100)
    private String owner;

    @Column(nullable = false, updatable = false, length = 100)
    private String repo;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "closed_unmerged_at")
    private Instant closedUnmergedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SpecQueueItemPullRequest() {
    }

    public SpecQueueItemPullRequest(UUID itemId, String workflowRunId, String url, String owner,
                                    String repo, int number, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.workflowRunId = workflowRunId;
        this.url = url;
        this.owner = owner;
        this.repo = repo;
        this.number = number;
        this.createdAt = createdAt;
    }

    public void markMerged(Instant at) {
        this.mergedAt = at;
    }

    public void markClosedUnmerged(Instant at) {
        this.closedUnmergedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getItemId() { return itemId; }
    public String getWorkflowRunId() { return workflowRunId; }
    public String getUrl() { return url; }
    public String getOwner() { return owner; }
    public String getRepo() { return repo; }
    public int getNumber() { return number; }
    public Instant getMergedAt() { return mergedAt; }
    public Instant getClosedUnmergedAt() { return closedUnmergedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
