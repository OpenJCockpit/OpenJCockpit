package nl.metafactory.agents.definition.config;

import nl.metafactory.agents.definition.demo.DemoAgentTools;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.source.LocalMarkdownAgentDefinitionProvider;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 29: health indicator, demo tool profile gating, and the shipped sample definitions. */
class AgentDefinitionsOperationsTest {

    @TempDir
    Path empty;

    @Test
    void healthReportsLocalAgentsAndSkills() {
        var root = new ConfinedRoot(Path.of("agent-definitions"));
        var health = new AgentDefinitionsHealthIndicator(root, new LocalSkillInventory(root, new SkillMarkdownParser())).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("localAgents", 5L).containsEntry("localSkills", 1);
    }

    @Test
    void healthIsUpButEmptyWithoutAgentsDirectory() {
        var root = new ConfinedRoot(empty);
        var health = new AgentDefinitionsHealthIndicator(root, new LocalSkillInventory(root, new SkillMarkdownParser())).health();
        assertThat(health.getDetails()).containsEntry("localAgents", 0L);
    }

    @Test
    void demoToolExistsOnlyInTheDemoProfile() {
        var runner = new ApplicationContextRunner().withUserConfiguration(DemoAgentTools.class);
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(RegisteredAgentTool.class));
        runner.withPropertyValues("spring.profiles.active=agent-demo")
                .run(ctx -> assertThat(ctx.getBean(RegisteredAgentTool.class).id()).isEqualTo("customer.read"));
    }

    @Test
    void shippedSampleDefinitionsAreValid() {
        var root = new ConfinedRoot(Path.of("agent-definitions"));
        var agent = new LocalMarkdownAgentDefinitionProvider(root, new AgentMarkdownParser()).lookup("customer-support");
        assertThat(agent.document()).isNotNull();
        assertThat(new LocalSkillInventory(root, new SkillMarkdownParser()).discover()).containsOnlyKeys("customer-lookup");
    }
}
