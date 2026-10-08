package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultWorkflowImporterTest {

    private WorkflowDefinitionProperties properties;
    private WorkflowDefinitionRepository workflowRepository;
    private WorkflowGroupRepository groupRepository;
    private DefaultWorkflowImporter importer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        var store = new YamlDefinitionStore();
        workflowRepository = new WorkflowDefinitionRepository(properties, store);
        groupRepository = new WorkflowGroupRepository(properties, store);
        importer = new DefaultWorkflowImporter(properties, workflowRepository, groupRepository, new DefaultWorkflowBundleReader());
    }

    @Test
    void importsTheThreeStandardWorkflowsAndTheirGroupOnStartup() {
        importer.run(new DefaultApplicationArguments());

        assertThat(groupRepository.findById("wg-spec-workflow")).isPresent();
        assertThat(workflowRepository.findAll())
                .extracting(WorkflowDefinition::id)
                .contains("wf-spec-init", "wf-spec-create", "wf-spec-implement", "wf-spec-realise");
        assertThat(workflowRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void specCreateWorkflowRequiresPromptAndOthersDoNot() {
        importer.importDefaults();

        assertThat(workflowRepository.findById("wf-spec-create").orElseThrow().promptRequired()).isTrue();
        assertThat(workflowRepository.findById("wf-spec-create").orElseThrow().promptInstructions())
                .contains("git branch")
                .contains(".specify/templates/spec-template.md")
                .contains("specs/_index.md")
                .contains("push the branch to origin so a pull request can be opened");
        assertThat(workflowRepository.findById("wf-spec-init").orElseThrow().promptRequired()).isFalse();
        assertThat(workflowRepository.findById("wf-spec-implement").orElseThrow().promptRequired()).isFalse();
    }

    @Test
    void seededWorkflowsAreGroupedAndReferenceKnownPipelineAgents() {
        importer.importDefaults();

        var implement = workflowRepository.findById("wf-spec-implement").orElseThrow();
        assertThat(implement.groupId()).isEqualTo("wg-spec-workflow");
        assertThat(implement.agentIds())
                .containsExactly("impact", "test-design", "implementation", "review", "evidence");

        var create = workflowRepository.findById("wf-spec-create").orElseThrow();
        assertThat(create.groupId()).isEqualTo("wg-spec-workflow");
        assertThat(create.agentIds()).containsExactly("requirement");

        var init = workflowRepository.findById("wf-spec-init").orElseThrow();
        assertThat(init.agentIds()).containsExactly("realisation");
    }

    @Test
    void seededGroupIsGlobalSoItsWorkflowsAreAvailableForEveryProject() {
        importer.importDefaults();

        var group = groupRepository.findById("wg-spec-workflow").orElseThrow();
        assertThat(group.projectName()).isEmpty();
        assertThat(workflowRepository.findById("wf-spec-init").orElseThrow().projectName()).isEmpty();
    }

    @Test
    void importIsIdempotentAndNeverOverwritesUserChanges() {
        importer.importDefaults();
        var seeded = workflowRepository.findById("wf-spec-create").orElseThrow();
        var customized = new WorkflowDefinition(seeded.id(), "My customized name", seeded.projectName(),
                seeded.groupId(), seeded.description(), seeded.agentIds(), seeded.subagentNames(),
                seeded.skillNames(), seeded.mcpTools(), seeded.trigger(), seeded.execution(),
                seeded.promptRequired(), seeded.promptInstructions(), seeded.status(),
                seeded.lastExecutionStatus(), seeded.lastExecutionAt(), null, null);
        workflowRepository.save(customized);

        importer.importDefaults();

        assertThat(workflowRepository.findById("wf-spec-create").orElseThrow().name())
                .isEqualTo("My customized name");
    }

    @Test
    void importCanBeDisabledViaProperties() {
        properties.setSeedDefaults(false);

        importer.run(new DefaultApplicationArguments());

        assertThat(workflowRepository.findAll()).isEmpty();
        assertThat(groupRepository.findAll()).isEmpty();
    }

    @Test
    void missingBundleResourceFailsWithClearError() {
        var brokenReader = new DefaultWorkflowBundleReader() { @Override InputStream openBundleResource() { return null; } };
        var broken = new DefaultWorkflowImporter(properties, workflowRepository, groupRepository, brokenReader);

        assertThatThrownBy(broken::importDefaults)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DefaultWorkflowImporter.DEFAULT_BUNDLE_RESOURCE);
    }

    @Test
    void unreadableBundleResourceIsWrappedInUncheckedIOException() {
        var brokenReader = new DefaultWorkflowBundleReader() { @Override InputStream openBundleResource() { return new InputStream() { @Override public int read() throws java.io.IOException { throw new java.io.IOException("broken"); } }; } };
        var broken = new DefaultWorkflowImporter(properties, workflowRepository, groupRepository, brokenReader);

        assertThatThrownBy(broken::importDefaults)
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void importHandlesBundleWithMissingLists() {
        var emptyReader = new DefaultWorkflowBundleReader() { @Override InputStream openBundleResource() { return new java.io.ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); } };
        var emptyBundle = new DefaultWorkflowImporter(properties, workflowRepository, groupRepository, emptyReader) {
        };

        emptyBundle.importDefaults();

        assertThat(workflowRepository.findAll()).isEmpty();
        assertThat(groupRepository.findAll()).isEmpty();
    }

    // ── Rollback tolerance (ADR-008, workflow-approval-gate batch B1) ──────────
    // readBundle runs at boot via ApplicationRunner; an unrecognised property on a workflow
    // (e.g. a gate field from a newer build) must be ignored, not fail application startup.

    @Test
    void importHandlesBundleWithAnUnknownWorkflowProperty() {
        var newerFormatReader = new DefaultWorkflowBundleReader() { @Override InputStream openBundleResource() { String json = "{\n  \"groups\": [],\n  \"workflows\": [\n    {\n      \"id\": \"wf-newer-format\",\n      \"name\": \"Newer format workflow\",\n      \"projectName\": \"Project\",\n      \"agentIds\": [\"requirement\"],\n      \"approvalGate\": {\"enabled\": true, \"placementStage\": \"realisation\"}\n    }\n  ]\n}"; return new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } };
        var newerFormatBundle = new DefaultWorkflowImporter(properties, workflowRepository, groupRepository, newerFormatReader) {
        };

        newerFormatBundle.importDefaults();

        assertThat(workflowRepository.findById("wf-newer-format")).isPresent();
    }

    // ── D-5: seeding path never writes populated runtime-state keys ─────────────

    @Test
    void seedingNeverWritesPopulatedRuntimeStateKeys() throws Exception {
        importer.importDefaults();

        var rawContent = java.nio.file.Files.readString(
                java.nio.file.Path.of(properties.getPath(), "workflows", "wf-spec-create.yaml"));
        assertThat(rawContent).doesNotContain("RUNNING");
        assertThat(rawContent).doesNotContain("COMPLETED");
        assertThat(rawContent).doesNotContain("FAILED");
        assertThat(rawContent).doesNotContain("CANCELLED");

        var reloaded = workflowRepository.findById("wf-spec-create").orElseThrow();
        assertThat(reloaded.lastExecutionStatus()).isNull();
        assertThat(reloaded.lastExecutionAt()).isNull();
    }
}
