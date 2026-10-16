package nl.metafactory.agents.definition.demo;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local demo only ({@code agent-demo} profile): a read-only {@code customer.read} tool returning a
 * fixed, fictitious status so the {@code customer-support} sample agent can run end to end without
 * any customer system. Never active in other profiles.
 */
@Configuration
@Profile("agent-demo")
public class DemoAgentTools {

    @Bean
    RegisteredAgentTool demoCustomerRead() {
        return new RegisteredAgentTool("customer.read", Tool.create("customer_read",
                "Returns the account status of the calling customer (demo data, no real customer system).",
                input -> Tool.Result.text("{\"status\":\"A\",\"meaning\":\"active\",\"demo\":true}")));
    }
}
