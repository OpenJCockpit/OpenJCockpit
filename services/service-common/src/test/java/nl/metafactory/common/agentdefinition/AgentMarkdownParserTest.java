package nl.metafactory.common.agentdefinition;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMarkdownParserTest {

    private final AgentMarkdownParser parser = new AgentMarkdownParser();

    static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/agent-definitions/agents/customer-support.md"));
    }

    @Test
    void parsesTheFixture() throws Exception {
        String raw = fixture();
        var doc = parser.parse(raw);

        assertThat(doc.id()).isEqualTo("customer-support");
        assertThat(doc.version()).isEqualTo("1.0.0");
        assertThat(doc.model()).isEqualTo("customer-chat");
        assertThat(doc.skills()).containsExactly("customer-lookup");
        assertThat(doc.tools()).containsExactly("customer.read");
        assertThat(doc.guardrails()).containsExactly("pii-protection");
        assertThat(doc.instructions()).startsWith("# Role").contains("their account");
        assertThat(doc.contentHash()).hasSize(64).isEqualTo(MarkdownFrontmatter.sha256(raw));
    }

    @Test
    void rejectsANameOutsideOneTo128Characters() {
        assertThatThrownBy(() -> parser.parse("---\nid: a\nname: \"" + "n".repeat(129) + "\"\nversion: 1\nmodel: m\n---\n# Body\n"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessage("name must be 1-128 characters");
    }

    @Test
    void crlfAndBomAreAccepted() throws Exception {
        var doc = parser.parse("﻿" + fixture().replace("\n", "\r\n"));
        assertThat(doc.id()).isEqualTo("customer-support");
    }

    @Test
    void rejectsMissingFrontmatter() {
        assertThatThrownBy(() -> parser.parse("# just markdown"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessageContaining("missing YAML frontmatter");
    }

    @Test
    void rejectsUnclosedFrontmatter() {
        assertThatThrownBy(() -> parser.parse("---\nid: x\n# body"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessageContaining("no closing");
    }

    @Test
    void rejectsUnknownKeys() {
        assertThatThrownBy(() -> parser.parse(doc("id: a\nversion: '1'\nmodel: m\nallowAllTools: true\n")))
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThatThrownBy(() -> parser.parse(doc("id: a\nid: b\nversion: '1'\nmodel: m\n")))
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    void rejectsPathLikeIdAndInvalidToolIds() {
        assertThatThrownBy(() -> parser.parse(doc("id: ../etc\nversion: '1'\nmodel: m\n")))
                .hasMessageContaining("id must be");
        assertThatThrownBy(() -> parser.parse(doc("id: a\nversion: '1'\nmodel: m\ntools: ['rm -rf']\n")))
                .hasMessageContaining("tools");
        assertThatThrownBy(() -> parser.parse(doc("id: a\nversion: '1'\nmodel: m\nskills: [x, x]\n")))
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsMissingRequiredFieldsAndEmptyBody() {
        assertThatThrownBy(() -> parser.parse(doc("id: a\nmodel: m\n"))).hasMessageContaining("version");
        assertThatThrownBy(() -> parser.parse("---\nid: a\nversion: '1'\nmodel: m\n---\n   \n"))
                .hasMessageContaining("body is empty");
    }

    @Test
    void rejectsOversizedDocument() {
        String huge = doc("id: a\nversion: '1'\nmodel: m\n") + "x".repeat(MarkdownFrontmatter.MAX_DOCUMENT_BYTES);
        assertThatThrownBy(() -> parser.parse(huge)).hasMessageContaining("exceeds");
    }

    @Test
    void errorMessageDoesNotEchoContent() {
        assertThatThrownBy(() -> parser.parse(doc("id: a\nversion: '1'\nmodel: m\nsecretKey: sk-live-123\n")))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("sk-live-123"));
    }

    private static String doc(String frontmatter) {
        return "---\n" + frontmatter + "---\n# Body\ntext\n";
    }
}
