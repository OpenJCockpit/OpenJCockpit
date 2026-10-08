package nl.metafactory.agents;

import com.embabel.agent.config.annotation.EnableAgents;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAgents
@EnableAsync
public class EmbabelAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbabelAgentServiceApplication.class, args);
    }
}
