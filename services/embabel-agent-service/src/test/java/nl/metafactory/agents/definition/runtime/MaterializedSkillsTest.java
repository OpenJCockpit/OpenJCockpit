package nl.metafactory.agents.definition.runtime;

import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillPackage;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    private static SkillPackage withResources(Map<String, String> resources) {
        return new SkillPackage("s", "desc", "# body", resources, List.of(), List.of(), "0".repeat(64));
    }

    @Test
    void aResourceOutsideItsSkillOrOneThatCannotBeWrittenFailsAndLeavesNothingBehind() throws Exception {
        long before = tempSkillDirs();

        assertThatThrownBy(() -> MaterializedSkills.of("agent", List.of(withResources(Map.of("../escape.md", "x")))))
                .isInstanceOf(IllegalStateException.class).hasMessage("resource path escapes skill directory");
        // a file and a folder with the same name cannot both be written
        assertThatThrownBy(() -> MaterializedSkills.of("agent",
                List.of(withResources(Map.of("a.md", "x", "a.md/b.md", "y")))))
                .isInstanceOf(java.io.UncheckedIOException.class);
        assertThat(tempSkillDirs()).isEqualTo(before);
    }

    @Test
    void closingTwiceOrADirectoryThatCannotBeRemovedNeverFails() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can delete anything");
        var materialized = MaterializedSkills.of("agent", List.of(withResources(Map.of("references/a.md", "v1"))));
        Path dir = materialized.directory();
        Path skill = dir.resolve("s");
        Files.setPosixFilePermissions(skill, PosixFilePermissions.fromString("r-x------"));
        materialized.close();
        assertThat(skill.resolve("SKILL.md")).exists();

        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------"));
        materialized.close();

        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(skill, PosixFilePermissions.fromString("rwx------"));
        materialized.close();
        assertThat(dir).doesNotExist();
        materialized.close();
    }

    private static long tempSkillDirs() throws Exception {
        try (var dirs = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return dirs.filter(p -> p.getFileName().toString().startsWith("agent-skills-")).count();
        }
    }
}
