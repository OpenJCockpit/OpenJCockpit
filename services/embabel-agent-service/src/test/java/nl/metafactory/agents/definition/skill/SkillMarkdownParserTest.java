package nl.metafactory.agents.definition.skill;

import nl.metafactory.agents.definition.markdown.InvalidDefinitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillMarkdownParserTest {

    private final SkillMarkdownParser parser = new SkillMarkdownParser();

    @Test
    void parsesNameDescriptionBodyAndAllowedTools() {
        var skill = parser.parse("---\nname: lookup\ndescription: Looks up\nallowed-tools: Read Grep\n---\n# Steps\n", "lookup");

        assertThat(skill.name()).isEqualTo("lookup");
        assertThat(skill.body()).isEqualTo("# Steps");
        assertThat(skill.allowedTools()).containsExactly("Read", "Grep");
    }

    @Test
    void rejectsInvalidYamlABadNameAndAnEmptyBody() {
        assertInvalid("---\nname: [unclosed\n---\n# Steps\n", "SKILL.md frontmatter is not valid YAML");
        assertInvalid("---\nname: Not A Slug\ndescription: d\n---\n# Steps\n", "SKILL.md name must be lowercase kebab-case");
        assertInvalid("---\nname: lookup\ndescription: d\n---\n\n", "SKILL.md body is empty");
    }

    private void assertInvalid(String raw, String message) {
        assertThatThrownBy(() -> parser.parse(raw, "lookup"))
                .isInstanceOf(InvalidDefinitionException.class).hasMessageStartingWith(message);
    }
}
