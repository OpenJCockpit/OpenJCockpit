package nl.metafactory.agents.definition.runtime;

import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillPackage;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaterializedSkillsTest {

    @TempDir
    Path tmp;

    @Test
    void onlySelectedSkillsAreVisibleAndScriptsAreAbsent() throws Exception {
        for (String id : List.of("selected", "other")) {
            Files.createDirectories(tmp.resolve("skills/" + id + "/scripts"));
            Files.writeString(tmp.resolve("skills/" + id + "/SKILL.md"),
                    "---\nname: " + id + "\ndescription: skill " + id + "\n---\n# " + id + "\nSee references/notes.md\n");
            Files.createDirectories(tmp.resolve("skills/" + id + "/references"));
            Files.writeString(tmp.resolve("skills/" + id + "/references/notes.md"), "notes for " + id);
            Files.writeString(tmp.resolve("skills/" + id + "/scripts/run.py"), "import os; os.system('rm -rf /')");
        }
        var inventory = new LocalSkillInventory(new ConfinedRoot(tmp), new SkillMarkdownParser());
        assertThat(inventory.discover()).containsOnlyKeys("selected", "other");

        try (var materialized = MaterializedSkills.of("agent", List.of(inventory.lookup("selected").skill()))) {
            var skills = materialized.skills();
            assertThat(skills.getSkills()).extracting(s -> s.getName()).containsExactly("selected");
            assertThat(skills.readResource("other", "references", "notes.md")).startsWith("Skill not found");
            assertThat(skills.readResource("selected", "references", "notes.md")).isEqualTo("notes for selected");
            assertThat(skills.readResource("selected", "scripts", "run.py")).startsWith("File not found");
            assertThat(skills.tools()).extracting(t -> t.getDefinition().getName())
                    .noneMatch(n -> n.startsWith("execute") || n.contains("run"));
            assertThat(materialized.references()).extracting(r -> r.getName()).contains("selected")
                    .doesNotContain("other");
        }
    }

    @Test
    void directoryIsRemovedOnCloseAndIsUnaffectedBySourceChanges() {
        var pkg = new SkillPackage("s", "desc", "# body", Map.of("references/a.md", "v1"), List.of(), List.of("Bash"),
                "0".repeat(64));
        Path dir;
        try (var materialized = MaterializedSkills.of("agent", List.of(pkg))) {
            dir = materialized.directory();
            assertThat(materialized.skills().readResource("s", "references", "a.md")).isEqualTo("v1");
            assertThat(MaterializedSkills.skillMarkdown(pkg)).doesNotContain("allowed-tools");
        }
        assertThat(dir).doesNotExist();
    }
}
