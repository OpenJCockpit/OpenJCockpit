package nl.metafactory.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_run_artifacts")
@IdClass(AgentRunArtifactId.class)
public class AgentRunArtifactRecord {

    @Id
    @Column(name = "run_id", length = 64)
    private String runId;

    @Id
    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Column(name = "artifact", length = 2000, nullable = false)
    private String artifact;

    public AgentRunArtifactRecord() {}

    public AgentRunArtifactRecord(String runId, Integer sequenceNo, String artifact) {
        this.runId = runId;
        this.sequenceNo = sequenceNo;
        this.artifact = artifact;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getArtifact() { return artifact; }
    public void setArtifact(String artifact) { this.artifact = artifact; }
}
