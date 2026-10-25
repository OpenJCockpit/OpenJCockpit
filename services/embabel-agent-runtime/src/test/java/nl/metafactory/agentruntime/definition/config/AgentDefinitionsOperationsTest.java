package nl.metafactory.agentruntime.definition.config;

import nl.metafactory.agentruntime.definition.demo.DemoAgentTools;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.agentruntime.definition.skill.LocalSkillInventory;
import nl.metafactory.agentruntime.definition.skill.SkillMarkdownParser;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import nl.metafactory.agentruntime.definition.source.LocalMarkdownAgentDefinitionProvider;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    @Test
    void healthIsDownWhenTheAgentsOrSkillsFolderCannotBeRead() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read any folder");
        var notListable = PosixFilePermissions.fromString("-wx------");
        Files.createDirectories(empty.resolve("agents"));
        Files.createDirectories(empty.resolve("skills"));
        var root = new ConfinedRoot(empty);
        var indicator = new AgentDefinitionsHealthIndicator(root, new LocalSkillInventory(root, new SkillMarkdownParser()));
        try {
            Files.setPosixFilePermissions(empty.resolve("agents"), notListable);
            assertThat(indicator.health().getDetails()).containsEntry("reason", "agents directory unreadable");

            Files.setPosixFilePermissions(empty.resolve("agents"), PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(empty.resolve("skills"), notListable);
            var health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("reason", "UncheckedIOException");
        } finally {
            Files.setPosixFilePermissions(empty.resolve("agents"), PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(empty.resolve("skills"), PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void theDemoToolAnswersWithADemoStatus() {
        new ApplicationContextRunner().withUserConfiguration(DemoAgentTools.class)
                .withPropertyValues("spring.profiles.active=agent-demo")
                .run(ctx -> assertThat(ctx.getBean(RegisteredAgentTool.class).tool().call("{}").toString())
                        .contains("\"demo\":true"));
    }
}
