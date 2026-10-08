package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentRunEntityMutationTest {

    @Test
    void agentRunRecordSettersAndGettersRoundTrip() {
        AgentRunRecord record = new AgentRunRecord();
        Instant startedAt = Instant.now().minusSeconds(30);
        Instant completedAt = Instant.now();
        Instant reconciledAt = Instant.now().minusSeconds(5);

        record.setRunId("run-x");
        record.setWorkflowId("wf-x");
        record.setCustomerId("cust-x");
        record.setSpecFile("spec.yaml");
        record.setRepositoryUrl("https://example.com/repo.git");
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        record.setCompletedAt(completedAt);
        record.setStartedBy("alice");
        record.setReconciledAt(reconciledAt);

        assertThat(record.getRunId()).isEqualTo("run-x");
        assertThat(record.getWorkflowId()).isEqualTo("wf-x");
        assertThat(record.getCustomerId()).isEqualTo("cust-x");
        assertThat(record.getSpecFile()).isEqualTo("spec.yaml");
        assertThat(record.getRepositoryUrl()).isEqualTo("https://example.com/repo.git");
        assertThat(record.getStatus()).isEqualTo("RUNNING");
        assertThat(record.getStartedAt()).isEqualTo(startedAt);
        assertThat(record.getCompletedAt()).isEqualTo(completedAt);
        assertThat(record.getStartedBy()).isEqualTo("alice");
        assertThat(record.getReconciledAt()).isEqualTo(reconciledAt);
    }

    @Test
    void agentRunEventRecordSettersAndGettersRoundTrip() {
        AgentRunEventRecord record = new AgentRunEventRecord();
        Instant occurredAt = Instant.now();

        record.setRunId("run-y");
        record.setSequenceNo(3);
        record.setOccurredAt(occurredAt);
        record.setAgentId("agent-y");
        record.setTitle("did something");
        record.setStatus("DONE");
        record.setEvidenceRef("evidence-ref");

        assertThat(record.getRunId()).isEqualTo("run-y");
        assertThat(record.getSequenceNo()).isEqualTo(3);
        assertThat(record.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(record.getAgentId()).isEqualTo("agent-y");
        assertThat(record.getTitle()).isEqualTo("did something");
        assertThat(record.getStatus()).isEqualTo("DONE");
        assertThat(record.getEvidenceRef()).isEqualTo("evidence-ref");
    }

    @Test
    void agentRunArtifactRecordSettersAndGettersRoundTrip() {
        AgentRunArtifactRecord record = new AgentRunArtifactRecord();

        record.setRunId("run-z");
        record.setSequenceNo(7);
        record.setArtifact("artifact-ref");

        assertThat(record.getRunId()).isEqualTo("run-z");
        assertThat(record.getSequenceNo()).isEqualTo(7);
        assertThat(record.getArtifact()).isEqualTo("artifact-ref");
    }
}
