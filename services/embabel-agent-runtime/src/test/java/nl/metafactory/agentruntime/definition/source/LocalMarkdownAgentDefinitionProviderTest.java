package nl.metafactory.agentruntime.definition.source;

import nl.metafactory.common.agentdefinition.ConfinedRoot;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.common.agentdefinition.InvalidDefinitionException;
import nl.metafactory.common.agentdefinition.SourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    @Test
    void anUnreadableDefinitionIsUnavailable() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read any file");
        Files.createDirectories(tmp.resolve("agents"));
        var file = Files.writeString(tmp.resolve("agents/locked.md"), "---\nid: locked\nversion: '1'\nmodel: m\n---\nx\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));

        var result = provider(tmp).lookup("locked");

        assertThat(result.status()).isEqualTo(SourceStatus.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo("local definition unreadable");
        assertThat(provider(tmp).source()).isEqualTo(nl.metafactory.agentruntime.definition.model.DefinitionSource.LOCAL);
    }
}
