package nl.metafactory.aicontrol;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgenticWorkflowProperties.class, SkillsMarketplaceProperties.class, SpecQueueProperties.class})
public class AiControlServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiControlServiceApplication.class, args);
    }
}
