package nl.metafactory.aicontrol.specqueue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import nl.metafactory.aicontrol.model.SpecQueueState;

import java.time.Instant;
import java.util.UUID;

/** Per-project queue settings and state. The row doubles as the per-project mutex (pessimistic lock). */
@Entity
@Table(name = "spec_queues")
public class SpecQueue {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpecQueueState state;

    @Column(name = "auto_merge_allowed", nullable = false)
    private boolean autoMergeAllowed;

    @Column(name = "settings_updated_by", length = 80)
    private String settingsUpdatedBy;

    @Column(name = "settings_updated_at")
    private Instant settingsUpdatedAt;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "last_poll_error_code", length = 40)
    private String lastPollErrorCode;

    @Column(name = "last_poll_error_at")
    private Instant lastPollErrorAt;

    @Column(name = "last_planner_decision", length = 40)
    private String lastPlannerDecision;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpecQueue() {
    }

    public SpecQueue(UUID projectId, Instant createdAt) {
        this.projectId = projectId;
        this.state = SpecQueueState.ACTIVE;
        this.autoMergeAllowed = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void changeState(SpecQueueState newState, Instant at) {
        this.state = newState;
        this.updatedAt = at;
    }

    public void changeAutoMergeAllowed(boolean allowed, String actorLabel, Instant at) {
        this.autoMergeAllowed = allowed;
        this.settingsUpdatedBy = actorLabel;
        this.settingsUpdatedAt = at;
        this.updatedAt = at;
    }

    public void recordPollSuccess(Instant at) {
        this.lastPolledAt = at;
        this.lastPollErrorCode = null;
        this.lastPollErrorAt = null;
        this.updatedAt = at;
    }

    public void recordPollError(String code, Instant at) {
        this.lastPollErrorCode = code;
        this.lastPollErrorAt = at;
        this.updatedAt = at;
    }

    public void rememberPlannerDecision(String decision, Instant at) {
        this.lastPlannerDecision = decision;
        this.updatedAt = at;
    }

    public UUID getProjectId() { return projectId; }
    public SpecQueueState getState() { return state; }
    public boolean isAutoMergeAllowed() { return autoMergeAllowed; }
    public String getSettingsUpdatedBy() { return settingsUpdatedBy; }
    public Instant getSettingsUpdatedAt() { return settingsUpdatedAt; }
    public Instant getLastPolledAt() { return lastPolledAt; }
    public String getLastPollErrorCode() { return lastPollErrorCode; }
    public Instant getLastPollErrorAt() { return lastPollErrorAt; }
    public String getLastPlannerDecision() { return lastPlannerDecision; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
