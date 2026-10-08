package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.AgentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSpecRepositoryTest {

    private AgentSpecRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        repository = new AgentSpecRepository(properties, new YamlDefinitionStore());
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindByNameRoundTrips() {
        var agent = new AgentSpec("triage-agent", "Triages incoming issues", "triage",
                "Read the issue and classify it", List.of(), List.of(), List.of(), "wf-1", false);

        repository.save(agent);

        assertThat(repository.findByName("triage-agent")).contains(agent);
    }

    @Test
    void findByNameReturnsEmptyWhenMissing() {
        assertThat(repository.findByName("missing")).isEmpty();
    }

    @Test
    void deleteByNameRemovesTheDefinition() {
        var agent = new AgentSpec("temp-agent", "d", "r", "i", List.of(), List.of(), List.of(), null, false);
        repository.save(agent);

        assertThat(repository.deleteByName("temp-agent")).isTrue();
        assertThat(repository.findByName("temp-agent")).isEmpty();
    }

    @Test
    void findAllSkipsAMalformedAgentSpecFileAndReturnsTheRemainingValidOnes(@TempDir Path tempDir) throws java.io.IOException {
        var a1 = new AgentSpec("triage-agent", "Triages incoming issues", "triage",
                "Read the issue and classify it", List.of(), List.of(), List.of(), "wf-1", false);
        var a2 = new AgentSpec("summary-agent", "Summarizes incoming issues", "summary",
                "Read the issue and summarize it", List.of(), List.of(), List.of(), "wf-1", false);
        repository.save(a1);
        repository.save(a2);
        var agentsDir = tempDir.resolve("agents");
        java.nio.file.Files.createDirectories(agentsDir);
        java.nio.file.Files.writeString(agentsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }
}
