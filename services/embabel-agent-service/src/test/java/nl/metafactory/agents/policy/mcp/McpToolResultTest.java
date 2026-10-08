package nl.metafactory.agents.policy.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolResultTest {

    @Test
    void blockedFactoryProducesBlockedResult() {
        var result = McpToolResult.blocked("requires human approval");

        assertThat(result.success()).isFalse();
        assertThat(result.blocked()).isTrue();
        assertThat(result.message()).isEqualTo("requires human approval");
        assertThat(result.output()).isNull();
    }
}
