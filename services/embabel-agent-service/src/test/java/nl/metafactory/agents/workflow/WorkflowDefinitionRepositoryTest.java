package nl.metafactory.agents.workflow;

import nl.metafactory.agents.approval.model.ApprovalGateConfig;
import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionRepositoryTest {

    private YamlDefinitionStore store;
    private Path workflowsDir;
    private WorkflowDefinitionRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        store = new YamlDefinitionStore();
        workflowsDir = tempDir.resolve("workflows");
        repository = new WorkflowDefinitionRepository(properties, store);
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindByIdRoundTrips() {
        var workflow = new WorkflowDefinition("wf-1", "Onboarding", "Noordzee Logistics", null, "desc",
                java.util.List.of("requirement"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);

        repository.save(workflow);

        assertThat(repository.findById("wf-1")).contains(workflow);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    void deleteByIdRemovesTheDefinition() {
        var workflow = new WorkflowDefinition("wf-2", "Name", "Project", null, "desc",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
        repository.save(workflow);

        assertThat(repository.deleteById("wf-2")).isTrue();
        assertThat(repository.findById("wf-2")).isEmpty();
    }

    @Test
    void saveGeneratesIdWhenBlank() {
        var workflow = new WorkflowDefinition("", "No id yet", "Project", null, "desc",
                java.util.List.of("requirement"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);

        var saved = repository.save(workflow);

        assertThat(saved.id()).isNotBlank();
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void saveGeneratesIdWhenNull() {
        var workflow = new WorkflowDefinition(null, "No id yet", "Project", null, "desc",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);

        var saved = repository.save(workflow);

        assertThat(saved.id()).isNotBlank();
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void findAllGeneratesIdForLegacyRecordMissingIdAndRemovesTheOldFile() {
        var legacy = new WorkflowDefinition("", "Legacy workflow", "Project", null, "desc",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
        store.save(workflowsDir, "", legacy);

        var all = repository.findAll();

        assertThat(all).hasSize(1);
        var repaired = all.get(0);
        assertThat(repaired.id()).isNotBlank();
        assertThat(repaired.name()).isEqualTo("Legacy workflow");
        assertThat(store.find(workflowsDir, "", WorkflowDefinition.class)).isEmpty();
        assertThat(repository.findById(repaired.id())).contains(repaired);
    }

    @Test
    void findByIdGeneratesIdForLegacyRecordMissingIdAndRemovesTheOldFile() {
        var legacy = new WorkflowDefinition(null, "Legacy workflow", "Project", null, "desc",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
        store.save(workflowsDir, "legacy", legacy);

        var found = repository.findById("legacy");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isNotBlank();
        assertThat(store.find(workflowsDir, "legacy", WorkflowDefinition.class)).isEmpty();
        assertThat(repository.findById(found.get().id())).contains(found.get());
    }

    // ── Gate configuration survival (AC-05/AC-06/BR-34) ─────────────────────────

    @Test
    void saveThenFindByIdRoundTripsAGateConfiguration() {
        var workflow = new WorkflowDefinition("wf-gate", "Onboarding", "Noordzee Logistics", null, "desc",
                java.util.List.of("requirement", "realisation"), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), null, null, false, null, "ACTIVE", null, null,
                new ApprovalGateConfig(true, "realisation"), null);

        repository.save(workflow);

        var found = repository.findById("wf-gate");
        assertThat(found).contains(workflow);
        assertThat(found.get().approvalGate().enabled()).isTrue();
        assertThat(found.get().approvalGate().placementStage()).isEqualTo("realisation");
    }

    @Test
    void loadingAPreFeatureYamlFileWithNoApprovalGateKeySucceedsAndReadsAsDisabled() throws Exception {
        // AC-05/BR-34: a workflow YAML written before this feature has no `approvalGate` key at
        // all. Batch B1's tolerant deserialization plus this field being nullable means it must
        // load successfully with the gate treated as disabled (null, not an exception).
        var fixture = Path.of("src/test/resources/workflows/pre-feature-workflow.yaml");
        Files.createDirectories(workflowsDir);
        Files.copy(fixture, workflowsDir.resolve("pre-feature-workflow.yaml"));

        var found = repository.findById("pre-feature-workflow");

        assertThat(found).isPresent();
        assertThat(found.get().approvalGate()).isNull();
        assertThat(repository.findAll()).extracting(WorkflowDefinition::id).contains("pre-feature-workflow");
    }

    @Test
    void findAllSkipsAMalformedDefinitionFileAndReturnsTheRemainingValidOnes() throws java.io.IOException {
        var wf1 = new WorkflowDefinition("wf-1", "Onboarding", "Noordzee Logistics", null, "desc",
                java.util.List.of("requirement"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
        var wf2 = new WorkflowDefinition("wf-2", "Offboarding", "Noordzee Logistics", null, "desc",
                java.util.List.of("requirement"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", null, null, null, null);
        repository.save(wf1);
        repository.save(wf2);
        Files.createDirectories(workflowsDir);
        Files.writeString(workflowsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }

    // ── D-4: persist(...) choke point strips lastExecutionStatus/lastExecutionAt ──

    @Test
    void saveStripsLastExecutionStatusAndLastExecutionAtFromTheOnDiskFile() throws Exception {
        var workflow = new WorkflowDefinition("wf-strip-1", "Onboarding", "Noordzee Logistics", null, "desc",
                java.util.List.of("requirement"), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", "RUNNING", java.time.Instant.now(), null, null);

        repository.save(workflow);

        var rawContent = Files.readString(workflowsDir.resolve("wf-strip-1.yaml"));
        assertThat(rawContent).doesNotContain("RUNNING");

        var reloaded = repository.findById("wf-strip-1").orElseThrow();
        assertThat(reloaded.lastExecutionStatus()).isNull();
        assertThat(reloaded.lastExecutionAt()).isNull();
    }

    @Test
    void ensureIdRepairPathAlsoStripsLastExecutionFields() throws Exception {
        var legacy = new WorkflowDefinition("", "Legacy with status", "Project", null, "desc",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, false, null, "ACTIVE", "CANCELLED", java.time.Instant.now(), null, null);
        store.save(workflowsDir, "some-temp-key", legacy);

        var all = repository.findAll();

        assertThat(all).hasSize(1);
        var repaired = all.get(0);
        assertThat(repaired.lastExecutionStatus()).isNull();
        assertThat(repaired.lastExecutionAt()).isNull();

        var rawContent = Files.readString(workflowsDir.resolve(repaired.id() + ".yaml"));
        assertThat(rawContent).doesNotContain("CANCELLED");
    }
}
