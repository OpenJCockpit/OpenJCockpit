package nl.metafactory.agents.definition.source;

import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import nl.metafactory.agents.definition.model.SourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMarkdownAgentDefinitionProviderTest {

    static final Path FIXTURES = Path.of("src/test/resources/agent-definitions");

    @TempDir
    Path tmp;

    private LocalMarkdownAgentDefinitionProvider provider(Path root) {
        return new LocalMarkdownAgentDefinitionProvider(new ConfinedRoot(root), new AgentMarkdownParser());
    }

    @Test
    void findsTheFixtureAgent() {
        var result = provider(FIXTURES).lookup("customer-support");
        assertThat(result.status()).isEqualTo(SourceStatus.FOUND);
        assertThat(result.document().id()).isEqualTo("customer-support");
    }

    @Test
    void unknownAgentIsNotFound() {
        assertThat(provider(FIXTURES).lookup("does-not-exist").status()).isEqualTo(SourceStatus.NOT_FOUND);
    }

    @Test
    void traversalIdsAreInvalidAndNeverTouchTheFileSystem() throws Exception {
        Files.writeString(tmp.resolve("secret.md"), "---\nid: secret\nversion: '1'\nmodel: m\n---\nx\n");
        Files.createDirectories(tmp.resolve("root/agents"));
        var p = provider(tmp.resolve("root"));
        for (String id : new String[]{"../secret", "..", "agents/../../secret", "/etc/passwd", "a\0b", "A"}) {
            assertThat(p.lookup(id).status()).as(id).isEqualTo(SourceStatus.INVALID);
        }
    }

    @Test
    void confinedRootRejectsDotDotSegments() {
        var root = new ConfinedRoot(FIXTURES);
        assertThatThrownBy(() -> root.resolve("agents", "..", "..")).isInstanceOf(InvalidDefinitionException.class);
        assertThatThrownBy(() -> root.resolve("agents/../x")).isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    void symlinkedAgentFileIsRejected() throws Exception {
        Path outside = tmp.resolve("outside.md");
        Files.copy(FIXTURES.resolve("agents/customer-support.md"), outside);
        Files.createDirectories(tmp.resolve("root/agents"));
        Files.createSymbolicLink(tmp.resolve("root/agents/customer-support.md"), outside);

        assertThat(provider(tmp.resolve("root")).lookup("customer-support").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void symlinkedAgentsDirectoryIsRejected() throws Exception {
        Files.createDirectories(tmp.resolve("root"));
        Files.createSymbolicLink(tmp.resolve("root/agents"), FIXTURES.toAbsolutePath().resolve("agents"));

        assertThat(provider(tmp.resolve("root")).lookup("customer-support").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void frontmatterIdMustMatchFileName() throws Exception {
        Files.createDirectories(tmp.resolve("agents"));
        Files.copy(FIXTURES.resolve("agents/customer-support.md"), tmp.resolve("agents/billing.md"));

        var result = provider(tmp).lookup("billing");
        assertThat(result.status()).isEqualTo(SourceStatus.INVALID);
        assertThat(result.reason()).contains("does not match");
    }

    @Test
    void malformedFileIsInvalid() throws Exception {
        Files.createDirectories(tmp.resolve("agents"));
        Files.writeString(tmp.resolve("agents/broken.md"), "no frontmatter");
        assertThat(provider(tmp).lookup("broken").status()).isEqualTo(SourceStatus.INVALID);
    }

    @Test
    void missingRootIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ConfinedRoot(tmp.resolve("nope"))).isInstanceOf(IllegalArgumentException.class);
    }
}
