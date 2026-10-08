package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.AgentPipelineProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWorkflowsSeedBundleTest {

    private final DefaultWorkflowBundleReader reader = new DefaultWorkflowBundleReader();

    @Test
    void bundleContainsAtLeastFourSeededWorkflows() {
        var bundle = reader.readBundle();

        assertThat(bundle.workflows()).isNotNull();
        assertThat(bundle.workflows()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void everySeededWorkflowHasNonEmptyAgentIds() {
        var bundle = reader.readBundle();

        for (WorkflowDefinition workflow : bundle.workflows()) {
            assertThat(workflow.agentIds())
                    .as("agentIds for workflow %s", workflow.id())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    void everySeededAgentIdIsAKnownPipelineAgent() {
        var bundle = reader.readBundle();
        List<String> knownAgents = new AgentPipelineProperties().getSequence();

        for (WorkflowDefinition workflow : bundle.workflows()) {
            assertThat(knownAgents)
                    .as("known pipeline agents for workflow %s", workflow.id())
                    .containsAll(workflow.agentIds());
        }
    }

    @Test
    void wfSpecInitAgentIdsIsExactlyRealisation() {
        var bundle = reader.readBundle();

        var wfSpecInit = bundle.workflows().stream()
                .filter(workflow -> "wf-spec-init".equals(workflow.id()))
                .findFirst()
                .orElseThrow();

        assertThat(wfSpecInit.agentIds()).containsExactly("realisation");
    }

    @Test
    void otherSeededWorkflowsRetainTheirExactAgentSets() {
        var bundle = reader.readBundle();

        var wfSpecCreate = bundle.workflows().stream()
                .filter(workflow -> "wf-spec-create".equals(workflow.id()))
                .findFirst()
                .orElseThrow();
        assertThat(wfSpecCreate.agentIds()).containsExactly("requirement");

        var wfSpecImplement = bundle.workflows().stream()
                .filter(workflow -> "wf-spec-implement".equals(workflow.id()))
                .findFirst()
                .orElseThrow();
        assertThat(wfSpecImplement.agentIds())
                .containsExactly("impact", "test-design", "implementation", "review", "evidence");

        var wfSpecRealise = bundle.workflows().stream()
                .filter(workflow -> "wf-spec-realise".equals(workflow.id()))
                .findFirst()
                .orElseThrow();
        assertThat(wfSpecRealise.agentIds())
                .containsExactly("impact", "test-design", "implementation", "review", "realisation", "evidence");
    }
}
