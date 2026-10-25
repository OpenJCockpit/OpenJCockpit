package nl.metafactory.agents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * The management plane for agents: workflows and their orchestration, the agent catalog, tenant bindings,
 * approval gates, policy decisions and run history. It runs no agent and no LLM itself — that is the job of
 * {@code embabel-agent-runtime}, reached over HTTP ({@code openjcockpit.agent-runtime.base-url}).
 */
@SpringBootApplication
@EnableAsync
public class EmbabelAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbabelAgentServiceApplication.class, args);
    }
}
