package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-008 (workflow-trigger-workflow-orb architecture §5.1, AC-48): proves that
 * {@link WorkflowDefinition#workflowOrbs()} being either {@code null} or an empty list produces
 * no {@code workflowOrbs} key at all in the persisted YAML, via the real
 * {@link YamlDefinitionStore} save path. This is the single test that would catch a missing or
 * mis-scoped {@code @JsonInclude} annotation, a regression that is otherwise invisible in normal
 * code review and would silently break byte-identical re-save of every pre-feature workflow
 * definition.
 */
class WorkflowOrbSerializationTest {

    private final YamlDefinitionStore store = new YamlDefinitionStore();

    private WorkflowDefinition minimalWorkflow(String id, List<nl.metafactory.agents.workflow.model.WorkflowOrb> orbs) {
        return new WorkflowDefinition(id, "Onboarding", "Noordzee Logistics", null, "desc",
                List.of("requirement"), List.of(), List.of(), List.of(), null, null, false, null,
                "ACTIVE", null, null, null, orbs);
    }

    @Test
    void nullWorkflowOrbsEmitsNoWorkflowOrbsKey(@TempDir Path tempDir) throws IOException {
        var definition = minimalWorkflow("wf-null-orbs", null);

        store.save(tempDir, "wf-null-orbs", definition);
        var yamlText = Files.readString(tempDir.resolve("wf-null-orbs.yaml"));

        assertThat(yamlText).doesNotContain("workflowOrbs");
    }

    @Test
    void emptyWorkflowOrbsEmitsNoWorkflowOrbsKey(@TempDir Path tempDir) throws IOException {
        var definition = minimalWorkflow("wf-empty-orbs", List.of());

        store.save(tempDir, "wf-empty-orbs", definition);
        var yamlText = Files.readString(tempDir.resolve("wf-empty-orbs.yaml"));

        assertThat(yamlText).doesNotContain("workflowOrbs");
    }
}
