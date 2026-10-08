package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R17 (architecture): Embabel 1.5.1's platform-autoconfigure module ships
 * {@code com.embabel.agent.autoconfigure.platform.QuiteMcpClientAutoConfiguration}, which extends
 * Spring AI's own {@code McpClientAutoConfiguration} and would register {@code McpSyncClient}/
 * {@code McpAsyncClient} beans (and therefore attempt real MCP connections at context startup)
 * unless {@code spring.ai.mcp.client.enabled: false} keeps it inactive. This module's own MCP
 * client wiring ({@link nl.metafactory.agents.mcp.McpConnectionFactory}/{@code SdkMcpServerConnection})
 * builds {@code McpSyncClient} instances by hand, internally, and never registers them as Spring
 * beans — so ANY {@code McpSyncClient}/{@code McpAsyncClient} bean found in this context would have
 * to come from that competing auto-configuration. This test proves the context boots with ZERO such
 * beans, i.e. no second/competing MCP client is created and no connection attempt happens at startup
 * from Spring AI's own auto-configuration path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class McpClientBeanCountBootTest {

    @MockitoBean ModelProvider modelProvider;

    @Autowired ApplicationContext context;

    @Test
    void noCompetingMcpSyncOrAsyncClientBeansExistInTheContext() {
        assertThat(context.getBeanNamesForType(McpSyncClient.class))
                .as("spring.ai.mcp.client.enabled=false must keep QuiteMcpClientAutoConfiguration inactive - "
                        + "no McpSyncClient bean may exist in the context")
                .isEmpty();
        assertThat(context.getBeanNamesForType(McpAsyncClient.class))
                .as("spring.ai.mcp.client.enabled=false must keep QuiteMcpClientAutoConfiguration inactive - "
                        + "no McpAsyncClient bean may exist in the context")
                .isEmpty();
    }
}
