package nl.metafactory.agentruntime.definition.skill;

import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.SourceStatus;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    @Test
    void aFileWhereASkillFolderShouldBeIsInvalid() throws Exception {
        Files.createDirectories(tmp.resolve("skills"));
        Files.writeString(tmp.resolve("skills/stray"), "not a folder");

        var lookup = inventory(tmp).lookup("stray");

        assertThat(lookup.status()).isEqualTo(SourceStatus.INVALID);
        assertThat(lookup.reason()).isEqualTo("skill stray is not a directory");
        assertThat(inventory(tmp).source()).isEqualTo(DefinitionSource.LOCAL);
    }

    @Test
    void onlyTextResourcesAreLoaded() throws Exception {
        skill("with-assets", "name: with-assets\ndescription: x\n");
        Files.writeString(tmp.resolve("skills/with-assets/logo.png"), "binary");
        Files.writeString(tmp.resolve("skills/with-assets/notes.md"), "notes");

        assertThat(inventory(tmp).lookup("with-assets").skill().resources()).containsOnlyKeys("notes.md");
    }

    @Test
    void aPackageWithTooManyOrTooLargeResourcesIsInvalid() throws Exception {
        skill("many", "name: many\ndescription: x\n");
        for (int i = 0; i <= LocalSkillInventory.MAX_RESOURCES; i++) {
            Files.writeString(tmp.resolve("skills/many/r" + i + ".md"), "x");
        }
        skill("large", "name: large\ndescription: x\n");
        for (int i = 0; i < 5; i++) {
            Files.writeString(tmp.resolve("skills/large/r" + i + ".md"), "x".repeat(250 * 1024));
        }

        assertThat(inventory(tmp).lookup("many").reason()).endsWith("skill package has too many resources");
        assertThat(inventory(tmp).lookup("large").reason()).endsWith("skill package exceeds 1048576 bytes");
    }

    @Test
    void unreadableSkillsAreUnavailableAndAnUnreadableSkillsFolderFailsDiscovery() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read any file");
        skill("locked-file", "name: locked-file\ndescription: x\n");
        Files.setPosixFilePermissions(tmp.resolve("skills/locked-file/SKILL.md"), PosixFilePermissions.fromString("---------"));
        skill("locked-dir", "name: locked-dir\ndescription: x\n");
        // traversable but not listable: SKILL.md can be read, the package's resources cannot be walked
        Files.setPosixFilePermissions(tmp.resolve("skills/locked-dir"), PosixFilePermissions.fromString("-wx------"));
        try {
            assertThat(inventory(tmp).lookup("locked-file").status()).isEqualTo(SourceStatus.UNAVAILABLE);
            assertThat(inventory(tmp).lookup("locked-dir").status()).isEqualTo(SourceStatus.UNAVAILABLE);

            Files.setPosixFilePermissions(tmp.resolve("skills"), PosixFilePermissions.fromString("-wx------"));
            assertThatThrownBy(() -> inventory(tmp).discover()).isInstanceOf(java.io.UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(tmp.resolve("skills"), PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(tmp.resolve("skills/locked-dir"), PosixFilePermissions.fromString("rwx------"));
        }
    }
}
