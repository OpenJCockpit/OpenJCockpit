package nl.metafactory.agentruntime;

import com.embabel.agent.config.annotation.EnableAgents;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The execution plane: hosts the Embabel agent platform, the configuration-driven agents, their LLM, tool
 * and guardrail integration, and the inbound A2A endpoints. Management data (tenant bindings, workflow
 * runs, policy decisions) is read from {@code embabel-agent-service} over its internal API.
 */
@SpringBootApplication
@EnableAgents
public class EmbabelAgentRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbabelAgentRuntimeApplication.class, args);
    }
}
