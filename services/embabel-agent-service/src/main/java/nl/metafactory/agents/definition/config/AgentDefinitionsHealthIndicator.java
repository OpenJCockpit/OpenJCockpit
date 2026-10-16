package nl.metafactory.agents.definition.config;

import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.stream.Stream;

/**
 * {@code /actuator/health/agentDefinitions}: the local definition root (the fallback that keeps the
 * service usable when LiteLLM is down) is readable and contains agents and conforming skills.
 * Deliberately not part of the readiness group: remote metadata outages are handled by fallback.
 */
@Component("agentDefinitions")
@ConditionalOnProperty(name = "openjcockpit.agent-definitions.enabled", havingValue = "true")
public class AgentDefinitionsHealthIndicator implements HealthIndicator {

    private final ConfinedRoot root;
    private final LocalSkillInventory skills;

    public AgentDefinitionsHealthIndicator(ConfinedRoot root, LocalSkillInventory skills) {
        this.root = root;
        this.skills = skills;
    }

    @Override
    public Health health() {
        try {
            long agents = root.resolve("agents").map(dir -> {
                try (Stream<java.nio.file.Path> files = Files.list(dir)) {
                    return files.filter(p -> p.toString().endsWith(".md")).count();
                } catch (java.io.IOException e) {
                    return -1L;
                }
            }).orElse(0L);
            if (agents < 0) {
                return Health.down().withDetail("reason", "agents directory unreadable").build();
            }
            return Health.up().withDetail("localAgents", agents).withDetail("localSkills", skills.discover().size()).build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("reason", e.getClass().getSimpleName()).build();
        }
    }
}
