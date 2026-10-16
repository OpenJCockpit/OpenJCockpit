package nl.metafactory.agents.workflow;

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
    void everySeededAgentIdIsACatalogAgentRegisteredInLiteLlm() throws Exception {
        var bundle = reader.readBundle();
        var registered = new java.util.HashSet<String>();
        for (var agent : tools.jackson.dataformat.yaml.YAMLMapper.builder().build()
                .readTree(java.nio.file.Path.of("../../infrastructure/litellm/config.yaml").toFile()).path("agents")) {
            registered.add("a2a:litellm:" + agent.path("agent_name").asString());
        }

        for (WorkflowDefinition workflow : bundle.workflows()) {
            assertThat(workflow.agentIds()).as("agent references of workflow %s", workflow.id())
                    .allMatch(nl.metafactory.agents.workflow.agents.AgentRef::isRef)
                    .allMatch(registered::contains);
        }
    }

    @Test
    void seededWorkflowsLinkTheDeliveryAgentsInChainOrder() {
        var bundle = reader.readBundle();
        java.util.function.Function<String, List<String>> agents = id -> bundle.workflows().stream()
                .filter(workflow -> id.equals(workflow.id())).findFirst().orElseThrow().agentIds();

        assertThat(agents.apply("wf-spec-init")).containsExactly("a2a:litellm:solution-architect");
        assertThat(agents.apply("wf-spec-create")).containsExactly("a2a:litellm:business-analyst");
        assertThat(agents.apply("wf-spec-implement")).containsExactly("a2a:litellm:solution-architect",
                "a2a:litellm:software-developer", "a2a:litellm:qa-engineer");
        assertThat(agents.apply("wf-spec-realise")).containsExactly("a2a:litellm:business-analyst",
                "a2a:litellm:solution-architect", "a2a:litellm:software-developer", "a2a:litellm:qa-engineer");
    }
}
