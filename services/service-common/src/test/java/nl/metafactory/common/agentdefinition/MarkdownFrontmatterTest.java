package nl.metafactory.common.agentdefinition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownFrontmatterTest {

    @Test
    void splitsFrontmatterFromTheBody() {
        var split = MarkdownFrontmatter.split("---\nid: a\n---\n\n# Body\n");

        assertThat(split.yaml()).isEqualTo("id: a\n");
        assertThat(split.body()).isEqualTo("# Body");
        assertThat(MarkdownFrontmatter.split("---\nid: a\n---").body()).isEmpty();
    }

    @Test
    void rejectsAMissingDocumentAndAClosingMarkerThatIsNotOnItsOwnLine() {
        assertThatThrownBy(() -> MarkdownFrontmatter.split(null))
                .isInstanceOf(InvalidDefinitionException.class).hasMessage("document is empty");
        assertThatThrownBy(() -> MarkdownFrontmatter.split("---\nid: a\n---body"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessage("closing --- must be on its own line");
    }

    @Test
    void anUnavailableDigestAlgorithmIsAnIllegalState() {
        assertThat(MarkdownFrontmatter.sha256("x")).hasSize(64);
        assertThatThrownBy(() -> MarkdownFrontmatter.digest("x", "NO-SUCH-DIGEST"))
                .isInstanceOf(IllegalStateException.class).hasMessage("NO-SUCH-DIGEST unavailable");
    }
}
