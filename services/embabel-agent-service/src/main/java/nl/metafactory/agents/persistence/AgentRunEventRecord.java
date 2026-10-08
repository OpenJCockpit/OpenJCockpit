package nl.metafactory.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_run_events")
@IdClass(AgentRunEventId.class)
public class AgentRunEventRecord {

    @Id
    @Column(name = "run_id", length = 64)
    private String runId;

    @Id
    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "agent_id", length = 128)
    private String agentId;

    @Column(name = "title", length = 2000)
    private String title;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "evidence_ref", length = 1000)
    private String evidenceRef;

    public AgentRunEventRecord() {}

    public AgentRunEventRecord(String runId, Integer sequenceNo, Instant occurredAt, String agentId,
                                String title, String status, String evidenceRef) {
        this.runId = runId;
        this.sequenceNo = sequenceNo;
        this.occurredAt = occurredAt;
        this.agentId = agentId;
        this.title = title;
        this.status = status;
        this.evidenceRef = evidenceRef;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
}
