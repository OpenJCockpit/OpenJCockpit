package nl.metafactory.agents.persistence;

import java.io.Serializable;
import java.util.Objects;

public class AgentRunArtifactId implements Serializable {

    private String runId;
    private Integer sequenceNo;

    public AgentRunArtifactId() {}

    public AgentRunArtifactId(String runId, Integer sequenceNo) {
        this.runId = runId;
        this.sequenceNo = sequenceNo;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentRunArtifactId that)) return false;
        return Objects.equals(runId, that.runId) && Objects.equals(sequenceNo, that.sequenceNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, sequenceNo);
    }
}
