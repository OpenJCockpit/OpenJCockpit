package nl.metafactory.agents.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRecordsTest {

    @Test
    void agentRunRecord() {
        var ts = Instant.now();
        var run = new AgentRun("run-1", "cust", "spec.md", "https://github.com/org/repo", "RUNNING", ts, List.of(), List.of("artifact.pdf"), "wf-1", "user-1", null, null);
        assertThat(run.runId()).isEqualTo("run-1");
        assertThat(run.customerId()).isEqualTo("cust");
        assertThat(run.specFile()).isEqualTo("spec.md");
        assertThat(run.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(run.status()).isEqualTo("RUNNING");
        assertThat(run.startedAt()).isEqualTo(ts);
        assertThat(run.events()).isEmpty();
        assertThat(run.generatedArtifacts()).containsExactly("artifact.pdf");
    }

    @Test
    void agentEventRecord() {
        var ts = Instant.now();
        var event = new AgentEvent(ts, "agent-id", "title", "OK", "evidence://ref");
        assertThat(event.timestamp()).isEqualTo(ts);
        assertThat(event.agentId()).isEqualTo("agent-id");
        assertThat(event.title()).isEqualTo("title");
        assertThat(event.status()).isEqualTo("OK");
        assertThat(event.evidenceRef()).isEqualTo("evidence://ref");
    }

    @Test
    void agentDefinitionRecord() {
        var def = new AgentDefinition("req", "Requirement Agent", "desc", "specification",
                List.of("spec.read"), List.of("requirements.yaml"), 0, "SpecContent", "RequirementAnalysis");
        assertThat(def.id()).isEqualTo("req");
        assertThat(def.name()).isEqualTo("Requirement Agent");
        assertThat(def.description()).isEqualTo("desc");
        assertThat(def.role()).isEqualTo("specification");
        assertThat(def.allowedTools()).containsExactly("spec.read");
        assertThat(def.requiredOutputs()).containsExactly("requirements.yaml");
        assertThat(def.sequenceOrder()).isEqualTo(0);
        assertThat(def.inputType()).isEqualTo("SpecContent");
        assertThat(def.outputType()).isEqualTo("RequirementAnalysis");
    }

    @Test
    void agentRunRequestRecord() {
        var req = new AgentRunRequest("cust", "spec.md", List.of("req", "impact"), "user1", "https://github.com/org/repo", "bot", "secret", null, "develop", "wf-1", "user1", nl.metafactory.agents.model.RunInitiator.trigger("test"), java.util.List.of("wf-test"));
        assertThat(req.customerId()).isEqualTo("cust");
        assertThat(req.specFile()).isEqualTo("spec.md");
        assertThat(req.agentIds()).containsExactly("req", "impact");
        assertThat(req.requestedBy()).isEqualTo("user1");
        assertThat(req.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(req.gitUsername()).isEqualTo("bot");
        assertThat(req.gitToken()).isEqualTo("secret");
        assertThat(req.approvalGate()).isNull();
        assertThat(req.baseBranch()).isEqualTo("develop");
        assertThat(req.workflowId()).isEqualTo("wf-1");
        assertThat(req.startedBy()).isEqualTo("user1");
        assertThat(req.initiator()).isEqualTo(nl.metafactory.agents.model.RunInitiator.trigger("test"));
        assertThat(req.chainAncestry()).containsExactly("wf-test");
    }
}
