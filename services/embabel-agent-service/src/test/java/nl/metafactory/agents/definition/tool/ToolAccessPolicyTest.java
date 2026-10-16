package nl.metafactory.agents.definition.tool;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolAccessPolicyTest {

    static RegisteredAgentTool tool(String id) {
        return new RegisteredAgentTool(id, Tool.create(RegisteredAgentTool.llmName(id), "test tool", in -> Tool.Result.text("ok")));
    }

    private final ToolAccessPolicy policy = new ToolAccessPolicy(
            new ToolRegistry(List.of(tool("customer.read"), tool("customer.delete"))));

    @Test
    void bindsRegisteredAndAllowedTools() {
        var bound = policy.authorize("customer-support", List.of("customer.read"), Set.of("customer.read"));
        assertThat(bound).extracting(RegisteredAgentTool::id).containsExactly("customer.read");
        assertThat(bound.getFirst().tool().getDefinition().getName()).isEqualTo("customer_read");
    }

    @Test
    void unknownToolIsDenied() {
        assertThatThrownBy(() -> policy.authorize("a", List.of("shell.exec"), Set.of("shell.exec")))
                .isInstanceOf(AgentResolutionException.class)
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.TOOL_DENIED);
    }

    @Test
    void registeredButNotAllowlistedToolIsDenied() {
        assertThatThrownBy(() -> policy.authorize("a", List.of("customer.delete"), Set.of("customer.read")))
                .hasMessageContaining("not allowed")
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(ResolutionFailure.TOOL_DENIED);
    }

    @Test
    void missingAllowlistDeniesEverything() {
        assertThatThrownBy(() -> policy.authorize("a", List.of("customer.read"), null))
                .isInstanceOf(AgentResolutionException.class);
        assertThat(policy.authorize("a", List.of(), null)).isEmpty();
    }

    @Test
    void registryRejectsDuplicatesAndNameClashes() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("a.b"), tool("a.b")))).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("a.b"), tool("a_b")))).hasMessageContaining("same LLM name");
    }

    @Test
    void toolNameMustMatchItsId() {
        assertThatThrownBy(() -> new RegisteredAgentTool("customer.read",
                Tool.create("customer_delete", "x", in -> Tool.Result.text("ok"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
