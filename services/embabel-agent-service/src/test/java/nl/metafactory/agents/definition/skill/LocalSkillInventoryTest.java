package nl.metafactory.agents.definition.skill;

import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSkillInventoryTest {

    static final Path FIXTURES = Path.of("src/test/resources/agent-definitions");

    @TempDir
    Path tmp;

    private LocalSkillInventory inventory(Path root) {
        return new LocalSkillInventory(new ConfinedRoot(root), new SkillMarkdownParser());
    }

    private void skill(String dir, String frontmatter) throws Exception {
        Files.createDirectories(tmp.resolve("skills").resolve(dir));
        Files.writeString(tmp.resolve("skills").resolve(dir).resolve("SKILL.md"), "---\n" + frontmatter + "---\n# Body\nDo it.\n");
    }

    @Test
    void loadsTheFixtureSkillWithReferences() {
        var lookup = inventory(FIXTURES).lookup("customer-lookup");
        assertThat(lookup.status()).isEqualTo(SourceStatus.FOUND);
        var skill = lookup.skill();
        assertThat(skill.description()).startsWith("Looks up");
        assertThat(skill.instructions()).contains("customer.read");
        assertThat(skill.resources()).containsOnlyKeys("references/status-codes.md");
        assertThat(skill.contentHash()).hasSize(64);
    }

    @Test
    void discoverReturnsOnlyConformingFolders() throws Exception {
        skill("good-skill", "name: good-skill\ndescription: fine\n");
        skill("name-mismatch", "name: other\ndescription: x\n");
        skill("no-description", "name: no-description\n");
        skill("Bad_Name", "name: Bad_Name\ndescription: x\n");
        skill("extra-key", "name: extra-key\ndescription: x\nauto-approve-tools: true\n");
        Files.createDirectories(tmp.resolve("skills/no-skill-file"));
        Files.writeString(tmp.resolve("skills/stray.md"), "not a folder");
        Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(tmp.resolve("outside/SKILL.md"), "---\nname: linked\ndescription: x\n---\nx\n");
        Files.createSymbolicLink(tmp.resolve("skills/linked"), tmp.resolve("outside"));

        assertThat(inventory(tmp).discover()).containsOnlyKeys("good-skill");
    }

    @Test
    void symlinkInsidePackageInvalidatesTheSkill() throws Exception {
        skill("leaky", "name: leaky\ndescription: x\n");
        Files.writeString(tmp.resolve("secret.txt"), "secret");
        Files.createDirectories(tmp.resolve("skills/leaky/references"));
        Files.createSymbolicLink(tmp.resolve("skills/leaky/references/s.txt"), tmp.resolve("secret.txt"));

        var lookup = inventory(tmp).lookup("leaky");
        assertThat(lookup.status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void scriptsAreListedButNeverLoaded() throws Exception {
        skill("with-script", "name: with-script\ndescription: x\nallowed-tools: customer.delete Bash\n");
        Files.createDirectories(tmp.resolve("skills/with-script/scripts"));
        Files.writeString(tmp.resolve("skills/with-script/scripts/run.sh"), "rm -rf /");

        var skill = inventory(tmp).lookup("with-script").skill();
        assertThat(skill.scriptPaths()).containsExactly("scripts/run.sh");
        assertThat(skill.resources()).isEmpty();
        assertThat(skill.declaredAllowedTools()).containsExactly("customer.delete", "Bash");
    }

    @Test
    void missingSkillsDirectoryYieldsEmptyInventory() {
        assertThat(inventory(tmp).discover()).isEmpty();
        assertThat(inventory(tmp).lookup("anything").status()).isEqualTo(SourceStatus.NOT_FOUND);
    }
}
