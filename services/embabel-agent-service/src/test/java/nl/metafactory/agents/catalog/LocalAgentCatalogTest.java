package nl.metafactory.agents.catalog;

import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class LocalAgentCatalogTest {

    @TempDir
    Path root;

    LocalAgentCatalog catalog(Path path) {
        var props = new AgentCatalogProperties();
        props.setLocalPath(path.toString());
        return new LocalAgentCatalog(props, new AgentMarkdownParser());
    }

    void agent(String relative, String id, String name) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nid: " + id + "\n" + (name == null ? "" : "name: \"" + name + "\"\n")
                + "version: \"1.0.0\"\ndescription: \"d " + id + "\"\nmodel: agent-chat\n---\n# Rol\nx\n");
    }

    @Test
    void listsAgentsRecursivelyWithFolderHierarchy() throws Exception {
        agent("triage.md", "triage", null);
        agent("delivery/analysis/business-analyst.md", "business-analyst", "Business Analyst");
        agent("delivery/qa-engineer.md", "qa-engineer", "QA Engineer");
        Files.writeString(root.resolve("README.md"), "# docs");
        Files.writeString(root.resolve("delivery/notes.txt"), "ignored");

        var section = catalog(root).list();

        assertThat(section.name()).isEqualTo("Local");
        assertThat(section.status()).isEqualTo("OK");
        assertThat(section.agents()).extracting(AgentCatalog.LocalAgent::path).containsExactly(
                "delivery/analysis/business-analyst.md", "delivery/qa-engineer.md", "triage.md");
        var ba = section.agents().getFirst();
        assertThat(ba.folders()).containsExactly("delivery", "analysis");
        assertThat(ba.name()).isEqualTo("Business Analyst");
        assertThat(ba.model()).isEqualTo("agent-chat");
        assertThat(ba.valid()).isTrue();
        assertThat(section.agents().get(2).folders()).isEmpty();
        assertThat(section.agents().get(2).name()).isEqualTo("triage");
    }

    @Test
    void invalidAndDuplicateFilesAreShownWithTheirProblem() throws Exception {
        Files.writeString(root.resolve("broken.md"), "no frontmatter");
        agent("a/dup.md", "dup", null);
        agent("b/dup.md", "dup", null);

        var agents = catalog(root).list().agents();
        assertThat(agents).filteredOn(a -> a.path().equals("broken.md")).singleElement()
                .satisfies(a -> {
                    assertThat(a.valid()).isFalse();
                    assertThat(a.problem()).contains("frontmatter");
                });
        assertThat(agents).filteredOn(a -> a.id().equals("dup")).hasSize(2)
                .allSatisfy(a -> assertThat(a.problem()).contains("duplicate"));
    }

    @Test
    void symlinksAreNeverFollowed(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("secret.md"), "---\nid: secret\nversion: '1'\nmodel: m\n---\nx\n");
        Files.createSymbolicLink(root.resolve("linked.md"), outside.resolve("secret.md"));
        Files.createSymbolicLink(root.resolve("linked-dir"), outside);
        assertThat(catalog(root).list().agents()).isEmpty();
    }

    @Test
    void missingFolderIsReportedNotThrown() {
        var section = catalog(root.resolve("nope")).list();
        assertThat(section.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(section.agents()).isEmpty();
    }

    @Test
    void anUnreadableFolderMakesTheSectionUnavailable() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read any folder");
        agent("delivery/qa-engineer.md", "qa-engineer", null);
        Files.setPosixFilePermissions(root.resolve("delivery"), PosixFilePermissions.fromString("---------"));
        try {
            var section = catalog(root).list();

            assertThat(section.status()).isEqualTo("UNAVAILABLE");
            assertThat(section.message()).isEqualTo("Local agents folder is not readable");
        } finally {
            Files.setPosixFilePermissions(root.resolve("delivery"), PosixFilePermissions.fromString("rwx------"));
        }
    }
}
