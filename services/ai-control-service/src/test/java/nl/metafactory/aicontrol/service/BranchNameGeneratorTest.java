package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BranchNameGeneratorTest {

    private BranchNameGenerator generator;

    @BeforeEach
    void setUp() {
        var props = new AgenticWorkflowProperties();
        props.setBranchPrefix("agentic");
        generator = new BranchNameGenerator(props);
    }

    @Test
    void generateReturnsGitSafeBranchName() {
        var project = projectWithName("My Project Name!");
        String branch = generator.generate(project, UUID.randomUUID());

        assertThat(branch).startsWith("agentic/my-project-name/");
        assertThat(branch).doesNotContain(" ").doesNotContain("!");
    }

    @Test
    void generateIncludesTimestampAndShortJobId() {
        UUID jobId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        var project = projectWithName("Test");
        String branch = generator.generate(project, jobId);

        assertThat(branch).contains("a1b2c3d4");
        assertThat(branch).matches("agentic/test/\\d{8}-\\d{6}-a1b2c3d4");
    }

    @Test
    void generateTwiceProducesUniqueNames() {
        var project = projectWithName("Alpha");
        String b1 = generator.generate(project, UUID.randomUUID());
        String b2 = generator.generate(project, UUID.randomUUID());
        assertThat(b1).isNotEqualTo(b2);
    }

    @Test
    void generateRespects200CharLimit() {
        var project = projectWithName("a".repeat(300));
        String branch = generator.generate(project, UUID.randomUUID());
        assertThat(branch.length()).isLessThanOrEqualTo(200);
    }

    @Test
    void toSlugReplacesSpecialCharsWithDash() {
        assertThat(generator.toSlug("Hello World!")).isEqualTo("hello-world");
        assertThat(generator.toSlug("Café & Co.")).isEqualTo("caf-co");
        assertThat(generator.toSlug("  leading  ")).isEqualTo("leading");
    }

    @Test
    void toSlugHandlesNullAndBlank() {
        assertThat(generator.toSlug(null)).isEqualTo("project");
        assertThat(generator.toSlug("  ")).isEqualTo("project");
    }

    @Test
    void generateWithSpecialProjectNameIsGitSafe() {
        var project = projectWithName("Absence Pro Solutions B.V.");
        String branch = generator.generate(project, UUID.randomUUID());
        assertThat(branch).doesNotContain(" ").doesNotContain(".");
        assertThat(branch).matches("[a-z0-9/\\-]+");
    }

    private Project projectWithName(String name) {
        var p = new Project();
        p.setName(name);
        p.setActive((short) 1);
        return p;
    }
}