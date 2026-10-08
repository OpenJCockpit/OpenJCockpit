package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.SkillSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSpecRepositoryTest {

    private SkillSpecRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        repository = new SkillSpecRepository(properties, new YamlDefinitionStore());
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindByNameRoundTrips() {
        var skill = new SkillSpec("summarize", "Summarizes text", "text", "summary",
                "Summarize the input", List.of(), null);

        repository.save(skill);

        assertThat(repository.findByName("summarize")).contains(skill);
    }

    @Test
    void findByNameReturnsEmptyWhenMissing() {
        assertThat(repository.findByName("missing")).isEmpty();
    }

    @Test
    void deleteByNameRemovesTheDefinition() {
        var skill = new SkillSpec("temp-skill", "d", "in", "out", "i", List.of(), null);
        repository.save(skill);

        assertThat(repository.deleteByName("temp-skill")).isTrue();
        assertThat(repository.findByName("temp-skill")).isEmpty();
    }

    @Test
    void findAllSkipsAMalformedSkillSpecFileAndReturnsTheRemainingValidOnes(@TempDir Path tempDir) throws java.io.IOException {
        var s1 = new SkillSpec("summarize", "Summarizes text", "text", "summary",
                "Summarize the input", List.of(), null);
        var s2 = new SkillSpec("translate", "Translates text", "text", "translation",
                "Translate the input", List.of(), null);
        repository.save(s1);
        repository.save(s2);
        var skillsDir = tempDir.resolve("skills");
        java.nio.file.Files.createDirectories(skillsDir);
        java.nio.file.Files.writeString(skillsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }
}
