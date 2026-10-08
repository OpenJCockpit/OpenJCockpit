package nl.metafactory.agents.config;

import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-22: a context booted with an unrecognised property key under
 * "openjcockpit.workflow-definitions" must still start, and the autowired
 * {@link WorkflowDefinitionProperties} must still bind "path" normally — relaxed binding for a
 * {@code @ConfigurationProperties} class that does not set {@code ignoreUnknownFields = false}
 * ignores unknown keys, generalising to any future property removal from this class.
 *
 * <p>Deliberately uses a key-agnostic unknown property ("some-removed-legacy-flag"), not the
 * literal removed key, so this file needs no CT-1 token exclusion.
 *
 * <p>Inherently non-vacuous: a binding failure fails context startup, which fails this test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "openjcockpit.workflow-definitions.some-removed-legacy-flag=true")
class WorkflowDefinitionPropertiesUnknownKeyBootTest {

    @TempDir
    static Path workflowDefinitionsDir;

    @DynamicPropertySource
    static void workflowDefinitionsPath(DynamicPropertyRegistry registry) {
        registry.add("openjcockpit.workflow-definitions.path", () -> workflowDefinitionsDir.toString());
    }

    @MockitoBean
    ModelProvider modelProvider;

    @Autowired
    WorkflowDefinitionProperties properties;

    @Test
    void contextStartsAndPathBindsNormallyDespiteUnknownProperty() {
        assertThat(properties.getPath()).isEqualTo(workflowDefinitionsDir.toString());
    }
}
