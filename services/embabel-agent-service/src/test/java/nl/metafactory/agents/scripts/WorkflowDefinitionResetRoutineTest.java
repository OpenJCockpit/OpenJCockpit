package nl.metafactory.agents.scripts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkflowDefinitionResetRoutineTest {

    private static final List<String> PRESERVED_DIRS =
            List.of("agents", "subagents", "skills", "decision-logs", "approval-decisions");
    private static final List<String> CLEAR_DIRS = List.of("workflows", "workflow-groups");
    private static final List<String> ALL_SEVEN_DIRS = List.of(
            "workflows", "workflow-groups", "agents", "subagents", "skills",
            "decision-logs", "approval-decisions");

    private Path repoRoot;
    private Path scriptPath;

    @BeforeEach
    void resolveScript() {
        repoRoot = repoRoot();
        scriptPath = repoRoot.resolve("scripts/lib/reset-workflow-definitions.sh");
        assertTrue(Files.exists(scriptPath), "reset routine script must exist at " + scriptPath);
    }

    private static Path repoRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("docker-compose.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate repository root (docker-compose.yml marker)");
    }

    private void seedAllSevenDirs(Path root) throws IOException {
        for (String dir : ALL_SEVEN_DIRS) {
            Path d = root.resolve(dir);
            Files.createDirectories(d);
            Files.writeString(d.resolve("sample.yaml"), "content-of-" + dir, StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> digestTree(Path root) throws IOException, NoSuchAlgorithmException {
        Map<String, String> digests = new TreeMap<>();
        if (!Files.exists(root)) {
            return digests;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = Files.readAllBytes(f);
                byte[] hash = md.digest(bytes);
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                digests.put(root.relativize(f).toString(), hex.toString());
            }
        }
        return digests;
    }

    private Map<String, String> digestOf(Path root, List<String> dirs) throws IOException, NoSuchAlgorithmException {
        Map<String, String> all = new HashMap<>();
        for (String dir : dirs) {
            Path d = root.resolve(dir);
            all.putAll(prefixed(dir, digestTree(d)));
        }
        return all;
    }

    private Map<String, String> prefixed(String prefix, Map<String, String> map) {
        Map<String, String> result = new HashMap<>();
        map.forEach((k, v) -> result.put(prefix + "/" + k, v));
        return result;
    }

    private ProcessResult run(Path workDir, Map<String, String> envOverrides, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("sh");
        command.add(scriptPath.toString());
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        if (envOverrides != null) {
            envOverrides.forEach((k, v) -> {
                if (v == null) {
                    pb.environment().remove(k);
                } else {
                    pb.environment().put(k, v);
                }
            });
        }
        pb.redirectErrorStream(false);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("reset-workflow-definitions.sh did not complete within timeout. stdout=" + stdout + " stderr=" + stderr);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {}

    @Test
    void clearEmptiesOnlyTheTwoTargetDirectories(@TempDir Path tempDir) throws Exception {
        seedAllSevenDirs(tempDir);
        Map<String, String> beforePreserved = digestOf(tempDir, PRESERVED_DIRS);

        ProcessResult result = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "clear", tempDir.toString());
        assertEquals(0, result.exitCode(), "clear should exit 0: stderr=" + result.stderr());

        for (String dir : CLEAR_DIRS) {
            Path d = tempDir.resolve(dir);
            try (Stream<Path> walk = Files.walk(d)) {
                boolean anyYaml = walk.filter(Files::isRegularFile)
                        .anyMatch(p -> p.getFileName().toString().endsWith(".yaml"));
                assertFalse(anyYaml, dir + " must contain no .yaml files after clear");
            }
        }

        Map<String, String> afterPreserved = digestOf(tempDir, PRESERVED_DIRS);
        assertEquals(beforePreserved, afterPreserved, "preserved directories must be byte-identical after clear");
    }

    @Test
    void inventoryModeWritesNothing(@TempDir Path tempDir) throws Exception {
        seedAllSevenDirs(tempDir);
        Map<String, String> before = digestOf(tempDir, ALL_SEVEN_DIRS);

        ProcessResult result = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "inventory", tempDir.toString());
        assertEquals(0, result.exitCode(), "inventory should exit 0: stderr=" + result.stderr());

        Map<String, String> after = digestOf(tempDir, ALL_SEVEN_DIRS);
        assertEquals(before, after, "inventory mode must not modify any file");
    }

    @Test
    void missingTargetDirectoriesResultInSuccessfulNoOp(@TempDir Path tempDir) throws Exception {
        for (String dir : PRESERVED_DIRS) {
            Path d = tempDir.resolve(dir);
            Files.createDirectories(d);
            Files.writeString(d.resolve("sample.yaml"), "content-of-" + dir, StandardCharsets.UTF_8);
        }

        ProcessResult result = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "clear", tempDir.toString());
        assertEquals(0, result.exitCode(), "clear must succeed even if workflows/workflow-groups are absent: stderr=" + result.stderr());

        for (String dir : CLEAR_DIRS) {
            assertFalse(Files.exists(tempDir.resolve(dir)), dir + " must not be created as a side effect");
        }
    }

    @Test
    void badRootRejectedWithoutDeletingAnything(@TempDir Path tempDir) throws Exception {
        seedAllSevenDirs(tempDir);
        Map<String, String> before = digestOf(tempDir, ALL_SEVEN_DIRS);

        Map<String, String> envOverrides = new HashMap<>();
        envOverrides.put("WORKFLOW_DEFINITIONS_PATH", null);
        ProcessResult result = run(tempDir, envOverrides, "clear");

        assertFalse(result.exitCode() == 0, "clear with no root at all must exit non-zero");

        Map<String, String> after = digestOf(tempDir, ALL_SEVEN_DIRS);
        assertEquals(before, after, "no file must be deleted when root resolution fails");
    }

    @Test
    void requireMountAgainstNonMountpointBehavesPerPlatform(@TempDir Path tempDir) throws Exception {
        seedAllSevenDirs(tempDir);

        ProcessResult result = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "--require-mount", "clear", tempDir.toString());

        if (Files.exists(Path.of("/proc/self/mountinfo"))) {
            assertFalse(result.exitCode() == 0,
                    "on a system with /proc/self/mountinfo, a plain temp dir should fail the mount check");
        } else {
            assertEquals(0, result.exitCode(),
                    "on a system without /proc/self/mountinfo, --require-mount should be skipped gracefully: stderr=" + result.stderr());
        }
    }

    @Test
    void repeatedClearIsIdempotent(@TempDir Path tempDir) throws Exception {
        seedAllSevenDirs(tempDir);
        Map<String, String> beforePreserved = digestOf(tempDir, PRESERVED_DIRS);

        ProcessResult first = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "clear", tempDir.toString());
        assertEquals(0, first.exitCode(), "first clear should succeed: stderr=" + first.stderr());

        ProcessResult second = run(tempDir, Map.of("WORKFLOW_DEFINITIONS_PATH", tempDir.toString()),
                "clear", tempDir.toString());
        assertEquals(0, second.exitCode(), "second clear should also succeed (idempotent): stderr=" + second.stderr());

        Map<String, String> afterPreserved = digestOf(tempDir, PRESERVED_DIRS);
        assertEquals(beforePreserved, afterPreserved, "preserved directories must remain untouched across both runs");
    }
}