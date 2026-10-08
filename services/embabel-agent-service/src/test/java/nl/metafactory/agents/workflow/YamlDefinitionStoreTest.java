package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.SkillSpec;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlDefinitionStoreTest {

    private final YamlDefinitionStore store = new YamlDefinitionStore();

    @Test
    void listReturnsEmptyWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
        var dir = tempDir.resolve("does-not-exist");
        assertThat(store.list(dir, SkillSpec.class)).isEmpty();
    }

    @Test
    void findReturnsEmptyWhenFileDoesNotExist(@TempDir Path tempDir) {
        assertThat(store.find(tempDir, "missing", SkillSpec.class)).isEmpty();
    }

    @Test
    void saveThenFindRoundTripsTheValue(@TempDir Path tempDir) {
        var skill = new SkillSpec("summarize", "Summarizes text", "text", "summary", "Summarize the input", List.of(), null);

        store.save(tempDir, skill.name(), skill);
        var found = store.find(tempDir, "summarize", SkillSpec.class);

        assertThat(found).contains(skill);
    }

    @Test
    void listReturnsAllSavedValuesSortedByFileName(@TempDir Path tempDir) {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        var b = new SkillSpec("beta", "B", "in", "out", "do b", List.of(), null);
        store.save(tempDir, a.name(), a);
        store.save(tempDir, b.name(), b);

        assertThat(store.list(tempDir, SkillSpec.class)).containsExactly(a, b);
    }

    @Test
    void listWithKeysReturnsEmptyWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
        var dir = tempDir.resolve("does-not-exist");
        assertThat(store.listWithKeys(dir, SkillSpec.class)).isEmpty();
    }

    @Test
    void listWithKeysExposesTheOnDiskFileNameForEachRecord(@TempDir Path tempDir) {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        store.save(tempDir, "alpha", a);
        // Save a record whose embedded name doesn't match the file it's stored under,
        // simulating a record that was persisted with a blank/mismatched id.
        var blank = new SkillSpec("", "Blank", "in", "out", "do blank", List.of(), null);
        store.save(tempDir, "", blank);

        var entries = store.listWithKeys(tempDir, SkillSpec.class);

        assertThat(entries).extracting(Map.Entry::getKey).containsExactly("", "alpha");
        assertThat(entries).extracting(Map.Entry::getValue).containsExactly(blank, a);
    }

    @Test
    void listWithKeysWrapsIOExceptionWhenDirectoryIsUnreadable(@TempDir Path tempDir) throws IOException {
        var dir = tempDir.resolve("locked");
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("-w-------"));

        try {
            assertThatThrownBy(() -> store.listWithKeys(dir, SkillSpec.class))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void deleteRemovesTheFileAndReturnsTrue(@TempDir Path tempDir) {
        var skill = new SkillSpec("temp", "Temp", "in", "out", "do temp", List.of(), null);
        store.save(tempDir, skill.name(), skill);

        assertThat(store.delete(tempDir, "temp")).isTrue();
        assertThat(store.find(tempDir, "temp", SkillSpec.class)).isEmpty();
    }

    @Test
    void deleteReturnsFalseWhenFileDoesNotExist(@TempDir Path tempDir) {
        assertThat(store.delete(tempDir, "missing")).isFalse();
    }

    @Test
    void saveCreatesDirectoryWhenMissing(@TempDir Path tempDir) {
        var dir = tempDir.resolve("nested/dir");
        var skill = new SkillSpec("x", "X", "in", "out", "do x", List.of(), null);

        store.save(dir, skill.name(), skill);

        assertThat(store.find(dir, "x", SkillSpec.class)).contains(skill);
    }

    @Test
    void findWrapsIOExceptionWhenFileContentIsUnreadable(@TempDir Path tempDir) throws IOException {
        // A YAML sequence cannot be mapped onto the SkillSpec record, forcing readValue to throw.
        Files.writeString(tempDir.resolve("broken.yaml"), "- not\n- an\n- object\n");

        assertThatThrownBy(() -> store.find(tempDir, "broken", SkillSpec.class))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void listWrapsIOExceptionWhenDirectoryIsUnreadable(@TempDir Path tempDir) throws IOException {
        var dir = tempDir.resolve("locked");
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("-w-------"));

        try {
            assertThatThrownBy(() -> store.list(dir, SkillSpec.class))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void saveWrapsIOExceptionWhenTargetIsADirectory(@TempDir Path tempDir) throws IOException {
        Files.createDirectory(tempDir.resolve("conflict.yaml"));
        var skill = new SkillSpec("conflict", "d", "in", "out", "i", List.of(), null);

        assertThatThrownBy(() -> store.save(tempDir, skill.name(), skill))
                .isInstanceOf(UncheckedIOException.class);
    }

    // ── Rollback tolerance (ADR-008, workflow-approval-gate batch B1) ──────────
    // FAIL_ON_UNKNOWN_PROPERTIES is disabled so a YAML file carrying a field unknown to this
    // build's record (e.g. written by a newer build) loads with that field silently ignored,
    // instead of throwing and bricking every repository built on this shared store.

    @Test
    void findIgnoresAnUnknownPropertyInsteadOfThrowing(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("newer.yaml"), """
                name: summarize
                description: Summarizes text
                inputContract: text
                outputContract: summary
                executionInstructions: Summarize the input
                mcpTools: []
                policyNotes: null
                approvalGate:
                  enabled: true
                  placementStage: realisation
                """);

        var found = store.find(tempDir, "newer", SkillSpec.class);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("summarize");
    }

    @Test
    void listIgnoresAnUnknownPropertyForADifferentRecordTypeToo(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("wg-1.yaml"), """
                id: wg-1
                name: Onboarding flows
                description: desc
                projectName: null
                futureField: something-not-yet-modelled
                """);

        var groups = store.list(tempDir, WorkflowGroup.class);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).id()).isEqualTo("wg-1");
    }

    @Test
    void deleteWrapsIOExceptionWhenTargetIsANonEmptyDirectory(@TempDir Path tempDir) throws IOException {
        var asDir = tempDir.resolve("nonempty.yaml");
        Files.createDirectory(asDir);
        Files.writeString(asDir.resolve("child.txt"), "content");

        assertThatThrownBy(() -> store.delete(tempDir, "nonempty"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void saveWrapsRealFileSystemIOExceptionInUncheckedIOException(@TempDir Path tempDir) throws IOException {
        // Create a regular file so that it cannot serve as an intermediate directory.
        Files.writeString(tempDir.resolve("not-a-directory"), "placeholder");

        var nestedDir = tempDir.resolve("not-a-directory").resolve("nested");
        var skill = new SkillSpec("x", "X", "in", "out", "do x", List.of(), null);

        assertThatThrownBy(() -> store.save(nestedDir, skill.name(), skill))
                .isInstanceOf(UncheckedIOException.class)
                .satisfies(ex -> assertThat(ex.getCause()).isInstanceOf(IOException.class));
    }

    @Test
    void readFailureMessageNamesTheRelativeFileForFind(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), "- not\n- an\n- object\n");

        assertThatThrownBy(() -> store.find(tempDir, "broken", SkillSpec.class))
                .hasMessageContaining("broken.yaml");
    }

    @Test
    void readFailureCauseChainReachesTheOriginalJacksonException(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), "- not\n- an\n- object\n");

        assertThatThrownBy(() -> store.find(tempDir, "broken", SkillSpec.class))
                .isInstanceOf(UncheckedIOException.class)
                .satisfies(ex -> assertThat(ex.getCause()).isInstanceOf(IOException.class))
                .satisfies(ex -> assertThat(ex.getCause().getCause()).isInstanceOf(tools.jackson.core.JacksonException.class));
    }

    @Test
    void saveFailureMessageNamesTheRelativeFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("not-a-directory"), "placeholder");

        var nestedDir = tempDir.resolve("not-a-directory").resolve("nested");
        var skill = new SkillSpec("x", "X", "in", "out", "do x", List.of(), null);

        assertThatThrownBy(() -> store.save(nestedDir, skill.name(), skill))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("x.yaml");
    }

    @Test
    void deleteFailureMessageNamesTheRelativeFile(@TempDir Path tempDir) throws IOException {
        var asDir = tempDir.resolve("nonempty.yaml");
        Files.createDirectory(asDir);
        Files.writeString(asDir.resolve("child.txt"), "content");

        assertThatThrownBy(() -> store.delete(tempDir, "nonempty"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("nonempty.yaml");
    }

    @Test
    void directoryListFailureMessageNamesTheRelativeDirectory(@TempDir Path tempDir) throws IOException {
        var dir = tempDir.resolve("locked");
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("-w-------"));

        try {
            assertThatThrownBy(() -> store.list(dir, SkillSpec.class))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("locked");
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void listSkipsAMalformedFileAndReturnsTheRemainingValidRecords(@TempDir Path tempDir) throws IOException {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        var b = new SkillSpec("beta", "B", "in", "out", "do b", List.of(), null);
        store.save(tempDir, a.name(), a);
        store.save(tempDir, b.name(), b);
        Files.writeString(tempDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        var result = store.list(tempDir, SkillSpec.class);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(a, b);
    }

    @Test
    void listEmitsExactlyOneWarnLogForOneMalformedFileAmongValidOnes(@TempDir Path tempDir) throws IOException {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        var b = new SkillSpec("beta", "B", "in", "out", "do b", List.of(), null);
        store.save(tempDir, a.name(), a);
        store.save(tempDir, b.name(), b);
        Files.writeString(tempDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YamlDefinitionStore.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            store.list(tempDir, SkillSpec.class);
        } finally {
            logger.detachAppender(appender);
        }

        var perFileWarns = appender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("definition.file.read.failed"))
                .toList();
        assertThat(perFileWarns).hasSize(1);
    }

    @Test
    void listEmitsNoWarnOrErrorLogsAcrossRepeatedListCallsOnACleanDirectory(@TempDir Path tempDir) throws IOException {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        var b = new SkillSpec("beta", "B", "in", "out", "do b", List.of(), null);
        var c = new SkillSpec("gamma", "C", "in", "out", "do c", List.of(), null);
        store.save(tempDir, a.name(), a);
        store.save(tempDir, b.name(), b);
        store.save(tempDir, c.name(), c);

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YamlDefinitionStore.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            store.list(tempDir, SkillSpec.class);
            store.list(tempDir, SkillSpec.class);
        } finally {
            logger.detachAppender(appender);
        }

        var warnOrHigher = appender.list.stream()
                .filter(e -> e.getLevel().toInt() >= ch.qos.logback.classic.Level.INFO.toInt())
                .toList();
        assertThat(warnOrHigher).isEmpty();
    }

    @Test
    void listEmitsOneDebugLinePerFilePlusASummaryDebugLineWhenNothingFails(@TempDir Path tempDir) throws IOException {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        var b = new SkillSpec("beta", "B", "in", "out", "do b", List.of(), null);
        var c = new SkillSpec("gamma", "C", "in", "out", "do c", List.of(), null);
        store.save(tempDir, a.name(), a);
        store.save(tempDir, b.name(), b);
        store.save(tempDir, c.name(), c);

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YamlDefinitionStore.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            store.list(tempDir, SkillSpec.class);
        } finally {
            logger.setLevel(null);
            logger.detachAppender(appender);
        }

        var perFileDebugs = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("definition.file.read path="))
                .toList();
        assertThat(perFileDebugs).hasSize(3);

        var summaryDebugs = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("definition.dir.scanned"))
                .filter(e -> e.getFormattedMessage().contains("loaded=3"))
                .filter(e -> e.getFormattedMessage().contains("failed=0"))
                .toList();
        assertThat(summaryDebugs).hasSize(1);
        assertThat(summaryDebugs.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
    }

    @Test
    void findStillThrowsAndNamesTheFileForAMalformedFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("bad.yaml"), "- not\n- an\n- object\n");

        assertThatThrownBy(() -> store.find(tempDir, "bad", SkillSpec.class))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("bad.yaml");
    }

    @Test
    void readFailureLeaksNoAbsolutePathNoFileContentAndNoRawJacksonMessage(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("secret-marker.yaml"), "- THIS_IS_A_UNIQUE_MARKER_STRING_98765\n- another\n- entry\n");

        UncheckedIOException thrown = null;
        try {
            store.find(tempDir, "secret-marker", SkillSpec.class);
        } catch (UncheckedIOException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();

        String rawJacksonMessage = null;
        try {
            var mapper = tools.jackson.dataformat.yaml.YAMLMapper.builder().build();
            mapper.readValue(tempDir.resolve("secret-marker.yaml").toFile(), SkillSpec.class);
        } catch (tools.jackson.core.JacksonException e) {
            rawJacksonMessage = e.getMessage();
        }
        assertThat(rawJacksonMessage).isNotNull();

        assertThat(thrown.getMessage()).doesNotContain(tempDir.toAbsolutePath().toString());
        assertThat(thrown.getMessage()).doesNotContain("THIS_IS_A_UNIQUE_MARKER_STRING_98765");
        assertThat(thrown.getMessage()).isNotEqualTo(rawJacksonMessage);
        assertThat(thrown.getMessage()).doesNotContain(rawJacksonMessage);
    }

    @Test
    void listSkipsAnEmptyYamlFileInsteadOfFailingTheListing(@TempDir Path tempDir) throws IOException {
        var a = new SkillSpec("alpha", "A", "in", "out", "do a", List.of(), null);
        store.save(tempDir, a.name(), a);
        Files.createFile(tempDir.resolve("empty.yaml"));

        var result = store.list(tempDir, SkillSpec.class);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(a);
    }

    @Test
    void findIgnoresAnUnknownPropertyWithNoWarnLogged(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("newer.yaml"), """
                name: summarize
                description: Summarizes text
                inputContract: text
                outputContract: summary
                executionInstructions: Summarize the input
                mcpTools: []
                policyNotes: null
                approvalGate:
                  enabled: true
                  placementStage: realisation
                """);

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YamlDefinitionStore.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var found = store.find(tempDir, "newer", SkillSpec.class);
            assertThat(found).isPresent();
        } finally {
            logger.detachAppender(appender);
        }

        var warnOrHigher = appender.list.stream()
                .filter(e -> e.getLevel().toInt() >= ch.qos.logback.classic.Level.WARN.toInt())
                .toList();
        assertThat(warnOrHigher).isEmpty();
    }

    @Test
    void listIgnoresAnUnknownPropertyForADifferentRecordTypeWithNoWarnLogged(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("wg-1.yaml"), """
                id: wg-1
                name: Onboarding flows
                description: desc
                projectName: null
                futureField: something-not-yet-modelled
                """);

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YamlDefinitionStore.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var groups = store.list(tempDir, WorkflowGroup.class);
            assertThat(groups).hasSize(1);
        } finally {
            logger.detachAppender(appender);
        }

        var warnOrHigher = appender.list.stream()
                .filter(e -> e.getLevel().toInt() >= ch.qos.logback.classic.Level.WARN.toInt())
                .toList();
        assertThat(warnOrHigher).isEmpty();
    }
}
