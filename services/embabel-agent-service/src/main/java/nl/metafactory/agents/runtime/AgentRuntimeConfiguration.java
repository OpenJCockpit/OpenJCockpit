package nl.metafactory.agents.runtime;

import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The service's side of the boundary to embabel-agent-runtime, plus the data it serves to the runtime. */
@Configuration
public class AgentRuntimeConfiguration {

    @Bean
    AgentRuntimeClient agentRuntimeClient(AgentRuntimeProperties properties) {
        return AgentRuntimeClient.create(properties);
    }

    @Bean
    AgentTenantBindingRepository agentTenantBindingRepository(JdbcClient jdbc) {
        return new AgentTenantBindingRepository(jdbc);
    }

    /** The shared agent Markdown contract (service-common): the catalog lists and validates with it. */
    @Bean
    AgentMarkdownParser agentMarkdownParser() {
        return new AgentMarkdownParser();
    }
}
