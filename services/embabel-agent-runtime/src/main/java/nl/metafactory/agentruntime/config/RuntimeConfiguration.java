package nl.metafactory.agentruntime.config;

import nl.metafactory.agentruntime.definition.runtime.AgentRunService;
import nl.metafactory.agentruntime.definition.runtime.CatalogAgentRuns;
import nl.metafactory.agentruntime.execution.AgentExecutionService;
import nl.metafactory.agentruntime.execution.ExecutionRegistry;
import nl.metafactory.agentruntime.management.AgentServiceClient;
import nl.metafactory.agentruntime.management.AgentServiceProperties;
import nl.metafactory.agentruntime.workflowrun.WorkflowRunScopeResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The runtime's boundary: its client of the agent service and its execution API. */
@Configuration
public class RuntimeConfiguration {

    @Bean
    AgentServiceClient agentServiceClient(AgentServiceProperties properties) {
        return new AgentServiceClient(properties);
    }

    @Bean
    WorkflowRunScopeResolver workflowRunScopeResolver(AgentServiceClient agentService) {
        return new WorkflowRunScopeResolver(agentService);
    }

    @Bean
    ExecutionRegistry executionRegistry() {
        return new ExecutionRegistry();
    }

    @Bean
    @ConditionalOnProperty(name = "openjcockpit.agent-definitions.enabled", havingValue = "true")
    AgentExecutionService agentExecutionService(AgentRunService served, CatalogAgentRuns catalog,
                                                WorkflowRunScopeResolver scopes, ExecutionRegistry registry) {
        return new AgentExecutionService(served, catalog, scopes, registry);
    }
}
