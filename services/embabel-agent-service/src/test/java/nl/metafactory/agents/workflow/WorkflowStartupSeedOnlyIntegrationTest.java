package nl.metafactory.agents.workflow;

import com.embabel.common.ai.model.ModelProvider;
import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A real Spring Boot context, booted twice against the same on-disk workflow-definitions
 * directory, proves that only the seed importer writes to that store at startup: an existing,
 * stale record with an empty {@code agentIds} is never seeded over and never repaired by any
 * other startup component, an existing customised record for an id the corrected default seed
 * also defines is left completely untouched, the importer still creates every id that was
 * genuinely absent, exactly three {@link org.springframework.boot.ApplicationRunner} beans exist
 * at startup with the importer running first, and a second boot over the same directory writes
 * nothing further.
 *
 * <p>Per AC-16's "no save() is performed" clause, absence of a write is proven via file-system
 * evidence — byte identity <em>and</em> unchanged last-modified time, captured once in
 * {@code @BeforeAll} before any Spring context exists, since capturing only after the first boot
 * cannot prove that first boot itself changed nothing.
 *
 * <p>Log-absence is deliberately not asserted here. Spring Boot's {@code LoggingSystem}
 * re-initialises the Logback context during {@code SpringApplication.run()} and detaches any
 * appender attached before that boot (e.g. in {@code @BeforeAll}), so a pre-boot-attached
 * {@code ListAppender} cannot reliably observe {@code ApplicationRunner} log output here — an
 * unreliable absence assertion is worse than none, because it would pass for the wrong reason.
 * Log absence is instead guaranteed structurally: a companion absence-proof test asserts the
 * class that used to emit those log lines is gone from the classpath entirely, and the runner-
 * count assertion below proves no other runner exists to emit them either. That is strictly
 * stronger than any appender assertion could be.
 *
 * <p>Test execution order deliberately differs from a naive "list in requirement order" reading:
 * the mutating human-remedy test runs last, after every byte/mtime-identity proof, so that its
 * deliberate write cannot be mistaken for an unwanted side effect of either boot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowStartupSeedOnlyIntegrationTest {

    private static final List<String> SEED_REALISE_STAGES = List.of(
            "impact", "test-design", "implementation", "review", "realisation", "evidence");

    private static final List<String> EXISTING_WF_SPEC_INIT_AGENT_IDS = List.of(
            "requirement", "impact", "test-design", "implementation", "review", "realisation", "evidence");

    @TempDir
    static Path workflowDefinitionsDir;

    private static byte[] preBootRealiseBytes;
    private static FileTime preBootRealiseMtime;
    private static byte[] preBootInitBytes;
    private static FileTime preBootInitMtime;

    @BeforeAll
    static void seedFixturesBeforeAnyBoot() throws IOException {
        Path workflowsDir = workflowDefinitionsDir.resolve("workflows");
        Files.createDirectories(workflowsDir);

        Path realiseFile = workflowsDir.resolve("wf-spec-realise.yaml");
        Files.writeString(realiseFile, """
                id: wf-spec-realise
                name: "Realise spec (code)"
                projectName: ""
                groupId: wg-spec-workflow
                description: desc
                agentIds: []
                status: ACTIVE
                """, StandardOpenOption.CREATE);

        Path initFile = workflowsDir.resolve("wf-spec-init.yaml");
        Files.writeString(initFile, """
                id: wf-spec-init
                name: "Spec folder initialization"
                projectName: ""
                groupId: wg-spec-workflow
                description: desc
                agentIds:
                  - requirement
                  - impact
                  - test-design
                  - implementation
                  - review
                  - realisation
                  - evidence
                status: ACTIVE
                """, StandardOpenOption.CREATE);

        preBootRealiseBytes = Files.readAllBytes(realiseFile);
        preBootRealiseMtime = Files.getLastModifiedTime(realiseFile);
        preBootInitBytes = Files.readAllBytes(initFile);
        preBootInitMtime = Files.getLastModifiedTime(initFile);
    }

    @DynamicPropertySource
    static void workflowDefinitionsPath(DynamicPropertyRegistry registry) {
        registry.add("metafactory.workflow-definitions.path", () -> workflowDefinitionsDir.toString());
    }

    @MockitoBean
    ModelProvider modelProvider;

    @Autowired
    WorkflowDefinitionRepository repository;

    @Autowired
    WorkflowGroupRepository groupRepository;

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper objectMapper;

    @Test
    @Order(1)
    void staleEmptyRecordIsNeitherSeededOverNorRepairedByAnyStartupComponent() throws IOException {
        Optional<WorkflowDefinition> stale = repository.findById("wf-spec-realise");
        assertThat(stale).isPresent();
        assertThat(stale.get().agentIds()).isEmpty();

        Path realiseFile = workflowDefinitionsDir.resolve("workflows").resolve("wf-spec-realise.yaml");
        assertThat(Files.readAllBytes(realiseFile)).isEqualTo(preBootRealiseBytes);
        assertThat(Files.getLastModifiedTime(realiseFile)).isEqualTo(preBootRealiseMtime);
    }

    @Test
    @Order(2)
    void existingWfSpecInitRecordIsUntouchedByTheCorrectedSeed() throws IOException {
        Optional<WorkflowDefinition> existing = repository.findById("wf-spec-init");
        assertThat(existing).isPresent();
        assertThat(existing.get().agentIds()).containsExactlyElementsOf(EXISTING_WF_SPEC_INIT_AGENT_IDS);
        assertThat(existing.get().agentIds()).isNotEqualTo(List.of("realisation"));

        Path initFile = workflowDefinitionsDir.resolve("workflows").resolve("wf-spec-init.yaml");
        assertThat(Files.readAllBytes(initFile)).isEqualTo(preBootInitBytes);
        assertThat(Files.getLastModifiedTime(initFile)).isEqualTo(preBootInitMtime);
    }

    @Test
    @Order(3)
    void importerDidRunAndDidCreateTheIdsThatWereAbsent() {
        assertThat(groupRepository.findById("wg-spec-workflow")).isPresent();

        Optional<WorkflowDefinition> create = repository.findById("wf-spec-create");
        assertThat(create).isPresent();
        assertThat(create.get().agentIds()).containsExactly("requirement");

        Optional<WorkflowDefinition> implement = repository.findById("wf-spec-implement");
        assertThat(implement).isPresent();
        assertThat(implement.get().agentIds()).containsExactly("impact", "test-design", "implementation", "review", "evidence");
    }

    @Test
    @Order(4)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void theOnlyStartupRunnersAreTheImporterAndTheSpecInitializerInThatOrder() {
        String[] runnerBeanNames = context.getBeanNamesForType(ApplicationRunner.class);
        assertThat(runnerBeanNames).containsExactlyInAnyOrder("defaultWorkflowImporter", "specWorkflowInitializer", "agentRunReconciliationService");

        List<ApplicationRunner> orderedRunners = context.getBeanProvider(ApplicationRunner.class).orderedStream().toList();
        assertThat(orderedRunners).isNotEmpty();
        assertThat(orderedRunners.get(0)).isInstanceOf(DefaultWorkflowImporter.class);
    }

    @Test
    @Order(5)
    void secondBootLeavesBothRecordsByteIdentical() throws IOException {
        Path realiseFile = workflowDefinitionsDir.resolve("workflows").resolve("wf-spec-realise.yaml");
        assertThat(Files.readAllBytes(realiseFile)).isEqualTo(preBootRealiseBytes);
        assertThat(Files.getLastModifiedTime(realiseFile)).isEqualTo(preBootRealiseMtime);

        Path initFile = workflowDefinitionsDir.resolve("workflows").resolve("wf-spec-init.yaml");
        assertThat(Files.readAllBytes(initFile)).isEqualTo(preBootInitBytes);
        assertThat(Files.getLastModifiedTime(initFile)).isEqualTo(preBootInitMtime);
    }

    @Test
    @Order(6)
    void theDocumentedHumanRemedyRestoresAStartableWorkflow() throws Exception {
        WorkflowDefinition current = repository.findById("wf-spec-realise").orElseThrow();
        WorkflowDefinition updated = current.withAgentIds(List.of("impact"));

        mockMvc.perform(put("/api/workflows/wf-spec-realise")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk());

        assertThat(repository.findById("wf-spec-realise").orElseThrow().agentIds())
                .containsExactly("impact");
    }
}
