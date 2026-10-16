package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.config.WorkflowTriggerProperties;
import nl.metafactory.agents.workflow.agents.InMemoryWorkflowAgentLinks;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A workflow consists of references to catalog agents, stored in the linking table — not in YAML. */
class WorkflowAgentLinkingTest {

    @TempDir
    Path root;

    InMemoryWorkflowAgentLinks links;
    WorkflowDefinitionRepository repository;

    static WorkflowDefinition workflow(String id, List<String> agents) {
        return new WorkflowDefinition(id, "Delivery", null, "wg-1", "desc", agents, List.of(), List.of(), List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        var props = new WorkflowDefinitionProperties();
        props.setPath(root.toString());
        links = new InMemoryWorkflowAgentLinks();
        repository = new WorkflowDefinitionRepository(props, new YamlDefinitionStore(), links);
    }

    @Test
    void agentsAreStoredInTheLinkingTableInOrderAndNotInTheYamlFile() throws Exception {
        var saved = repository.save(workflow("wf-1", List.of("a2a:litellm:business-analyst", "local:triage")));

        assertThat(saved.agentIds()).containsExactly("a2a:litellm:business-analyst", "local:triage");
        assertThat(links.find("wf-1")).containsExactly("a2a:litellm:business-analyst", "local:triage");
        assertThat(repository.findById("wf-1").orElseThrow().agentIds())
                .containsExactly("a2a:litellm:business-analyst", "local:triage");
        assertThat(Files.readString(root.resolve("workflows/wf-1.yaml")))
                .doesNotContain("business-analyst").doesNotContain("triage");
    }

    @Test
    void deletingAWorkflowDeletesItsLinks() {
        repository.save(workflow("wf-1", List.of("local:triage")));
        repository.deleteById("wf-1");
        assertThat(links.find("wf-1")).isEmpty();
    }

    @Test
    void builtInStageIdsInAPreCatalogFileAreDroppedAndNotRewritten() throws Exception {
        Files.createDirectories(root.resolve("workflows"));
        Path file = root.resolve("workflows/wf-old.yaml");
        Files.writeString(file, "id: wf-old\nname: Old\ngroupId: wg-1\nagentIds:\n  - requirement\n  - realisation\nstatus: ACTIVE\n");
        byte[] before = Files.readAllBytes(file);

        assertThat(repository.findById("wf-old").orElseThrow().agentIds()).isEmpty();
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }

    @Test
    void validatorAcceptsCatalogReferencesOnly() {
        var validator = new WorkflowDefinitionValidator(new WorkflowOrbValidator(null, null, new WorkflowTriggerProperties()),
                ref -> ref.agentId().equals("missing") ? Optional.of("Local agent 'missing' does not exist") : Optional.empty());

        validator.validateForWrite(workflow("wf-1", List.of("a2a:litellm:qa-engineer", "local:triage")));

        assertThatThrownBy(() -> validator.validateForWrite(workflow("wf-1", List.of("requirement"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not an agent reference");
        assertThatThrownBy(() -> validator.validateForWrite(workflow("wf-1", List.of("local:missing"))))
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> validator.validateForWrite(workflow("wf-1", List.of("local:triage", "local:triage"))))
                .hasMessageContaining("only once");
        // imports check syntax only (the catalog may not be reachable yet)
        validator.validateStructuralRulesForImport(workflow("wf-1", List.of("local:missing")));
    }
}
