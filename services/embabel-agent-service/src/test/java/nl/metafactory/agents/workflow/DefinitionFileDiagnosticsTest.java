package nl.metafactory.agents.workflow;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.io.ContentReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionFileDiagnosticsTest {

    @Test
    void relativeNameJoinsLastTwoSegmentsWithSlash() {
        assertThat(DefinitionFileDiagnostics.relativeName(Path.of("workflows", "wf-x.yaml")))
                .isEqualTo("workflows/wf-x.yaml");
    }

    @Test
    void relativeNameReturnsFileNameWhenThereIsNoParent() {
        assertThat(DefinitionFileDiagnostics.relativeName(Path.of("wf-x.yaml")))
                .isEqualTo("wf-x.yaml");
    }

    @Test
    void relativeNameReturnsFileNameWhenParentHasNoFileName() {
        assertThat(DefinitionFileDiagnostics.relativeName(Path.of("/wf-x.yaml")))
                .isEqualTo("wf-x.yaml");
    }

    @Test
    void relativeNameFallsBackToPathStringWhenFileNameIsNull() {
        Path root = Path.of("/");
        assertThat(DefinitionFileDiagnostics.relativeName(root)).isEqualTo(String.valueOf(root));
    }

    @Test
    void sanitisedReasonReturnsSimpleClassNameForNonJacksonThrowable() {
        assertThat(DefinitionFileDiagnostics.sanitisedReason(new RuntimeException("boom")))
                .isEqualTo("RuntimeException");
    }

    @Test
    void sanitisedReasonAppendsLineAndColumnForRealParseFailure() {
        YAMLMapper mapper = YAMLMapper.builder().build();
        JacksonException caught = null;
        try {
            mapper.readValue("key: [unterminated", Map.class);
        } catch (JacksonException e) {
            caught = e;
        }
        assertThat(caught).isNotNull();
        assertThat(caught.getLocation()).isNotNull();
        assertThat(caught.getLocation()).isNotEqualTo(TokenStreamLocation.NA);

        String reason = DefinitionFileDiagnostics.sanitisedReason(caught);

        assertThat(reason).startsWith(caught.getClass().getSimpleName());
        assertThat(reason).contains("at line");
        assertThat(reason).contains("column");
    }

    @Test
    void sanitisedReasonReturnsSimpleClassNameWhenLocationIsNull() {
        YAMLMapper mapper = YAMLMapper.builder().build();
        JacksonException caught = null;
        try {
            mapper.readValue("key: [unterminated", Map.class);
        } catch (JacksonException e) {
            caught = e;
        }
        assertThat(caught).isNotNull();

        caught.clearLocation();

        assertThat(DefinitionFileDiagnostics.sanitisedReason(caught))
                .isEqualTo(caught.getClass().getSimpleName());
    }

    @Test
    void sanitisedReasonReturnsSimpleClassNameWhenLocationIsNa() {
        YAMLMapper mapper = YAMLMapper.builder().build();
        JacksonException real = null;
        try {
            mapper.readValue("key: [unterminated", Map.class);
        } catch (JacksonException e) {
            real = e;
        }
        assertThat(real).isNotNull();

        JacksonException spy = Mockito.spy(real);
        Mockito.when(spy.getLocation()).thenReturn(TokenStreamLocation.NA);

        assertThat(DefinitionFileDiagnostics.sanitisedReason(spy))
                .isEqualTo(spy.getClass().getSimpleName());
    }

    @Test
    void sanitisedReasonReturnsSimpleClassNameWhenLineAndColumnAreNegative() {
        YAMLMapper mapper = YAMLMapper.builder().build();
        JacksonException real = null;
        try {
            mapper.readValue("key: [unterminated", Map.class);
        } catch (JacksonException e) {
            real = e;
        }
        assertThat(real).isNotNull();

        TokenStreamLocation negativeLocation =
                new TokenStreamLocation(ContentReference.unknown(), -1L, -1L, -1, -1);
        JacksonException spy = Mockito.spy(real);
        Mockito.when(spy.getLocation()).thenReturn(negativeLocation);

        assertThat(DefinitionFileDiagnostics.sanitisedReason(spy))
                .isEqualTo(spy.getClass().getSimpleName());
    }
}
