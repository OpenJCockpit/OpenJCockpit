package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.SubagentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentSpecRepositoryTest {

    private SubagentSpecRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        repository = new SubagentSpecRepository(properties, new YamlDefinitionStore());
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindByNameRoundTrips() {
        var subagent = new SubagentSpec("log-collector", "triage-agent", "Collects logs",
                "Gather relevant logs", "Query the log store", List.of(), List.of(), "wf-1");

        repository.save(subagent);

        assertThat(repository.findByName("log-collector")).contains(subagent);
    }

    @Test
    void findByNameReturnsEmptyWhenMissing() {
        assertThat(repository.findByName("missing")).isEmpty();
    }

    @Test
    void deleteByNameRemovesTheDefinition() {
        var subagent = new SubagentSpec("temp-sub", "parent", "d", "r", "i", List.of(), List.of(), null);
        repository.save(subagent);

        assertThat(repository.deleteByName("temp-sub")).isTrue();
        assertThat(repository.findByName("temp-sub")).isEmpty();
    }

    @Test
    void findAllSkipsAMalformedSubagentSpecFileAndReturnsTheRemainingValidOnes(@TempDir Path tempDir) throws java.io.IOException {
        var s1 = new SubagentSpec("log-collector", "triage-agent", "Collects logs",
                "Gather relevant logs", "Query the log store", List.of(), List.of(), "wf-1");
        var s2 = new SubagentSpec("metric-collector", "triage-agent", "Collects metrics",
                "Gather relevant metrics", "Query the metrics store", List.of(), List.of(), "wf-1");
        repository.save(s1);
        repository.save(s2);
        var subagentsDir = tempDir.resolve("subagents");
        java.nio.file.Files.createDirectories(subagentsDir);
        java.nio.file.Files.writeString(subagentsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }
}
