package nl.metafactory.agents.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Absence proof (ADR-004, AC-15): this is the ONE file in the entire repository outside
 * {@code docs/delivery/**} permitted to contain the eight forbidden reconciler-era tokens
 * listed below. Its name declares its purpose, and it asserts its own exclusion set has
 * exactly one entry, so the exclusion cannot silently grow.
 *
 * <p>A plain JUnit test with no Spring context, so it is fast and does not depend on any
 * context configuration succeeding.
 */
class StartupAgentIdRepairAbsenceTest {

    private static final String REMOVED_CLASS_NAME = "nl.metafactory.agents.workflow.WorkflowAgentIdsReconciler";

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "WorkflowAgentIdsReconciler",
            "reconcileAgentIds",
            "reconcile-agent-ids",
            "Agent-id reconciliation",
            "Reconciled agentIds",
            "SEED_RESYNC",
            "CATALOGUE_BACKFILL",
            "CATALOGUE_BACKFILL_GATE_FALLBACK"
    );

    private static final Path THIS_TEST_SOURCE = Paths.get(
            "src", "test", "java", "nl", "metafactory", "agents", "workflow", "StartupAgentIdRepairAbsenceTest.java");

    @Test
    void classIsAbsentFromTheEntireTestClasspath() {
        assertThatThrownBy(() -> Class.forName(REMOVED_CLASS_NAME))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void propertiesExposeNoRepairSwitch() {
        List<String> fieldNames = Arrays.stream(nl.metafactory.agents.config.WorkflowDefinitionProperties.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        List<String> methodNames = Arrays.stream(nl.metafactory.agents.config.WorkflowDefinitionProperties.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("reconcileAgentIds");
        assertThat(methodNames).doesNotContain("isReconcileAgentIds", "setReconcileAgentIds");
    }

    @Test
    void noForbiddenTokenSurvivesAnywhereInThisModule() throws IOException {
        Set<Path> exclusions = Set.of(THIS_TEST_SOURCE);
        assertThat(exclusions).hasSize(1);

        Path srcRoot = Path.of("src");
        assertThat(Files.isDirectory(srcRoot))
                .as("Surefire's working directory must be the module basedir so 'src' resolves relatively")
                .isTrue();

        int[] scannedCount = {0};
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(srcRoot)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                scannedCount[0]++;
                if (exclusions.contains(path.normalize())) {
                    return;
                }
                String content;
                try {
                    content = Files.readString(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                for (String token : FORBIDDEN_TOKENS) {
                    if (content.contains(token)) {
                        violations.add(path + " contains forbidden token '" + token + "'");
                    }
                }
            });
        }

        assertThat(scannedCount[0])
                .as("must actually scan a meaningful number of files, not silently scan zero or almost zero")
                .isGreaterThan(200);
        assertThat(violations).isEmpty();
    }
}
