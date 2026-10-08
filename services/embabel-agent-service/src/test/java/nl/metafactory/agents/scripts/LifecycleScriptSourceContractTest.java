package nl.metafactory.agents.scripts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LifecycleScriptSourceContractTest {

    private static final Pattern STOP_REGEX =
            Pattern.compile(".*docker compose( +-f +\\S+)* +stop.*", Pattern.DOTALL);
    private static final Pattern BARE_DASH_V =
            Pattern.compile("(?<![\\w-])-v(?![\\w-])");
    private static final List<String> FORBIDDEN_TOKENS =
            List.of("down", "--volumes", "docker volume rm", "docker volume prune", "rm -rf");
    private static final List<String> PRESERVE_NAMES = List.of(
            "decision-logs", "approval-decisions", "agents", "subagents",
            "skills", "workflow-workspaces", "git-mcp-workspaces");

    private static Path repoRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && candidate != null; i++) {
            if (Files.exists(candidate.resolve("docker-compose.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate repository root (docker-compose.yml marker) "
                + "walking up from " + Paths.get("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOExceptionLike(path, e);
        }
    }

    private static final class UncheckedIOExceptionLike extends RuntimeException {
        UncheckedIOExceptionLike(Path path, IOException cause) {
            super("Failed to read " + path, cause);
        }
    }

    private static void assertNoForbiddenTokens(String content, String label) {
        for (String token : FORBIDDEN_TOKENS) {
            assertFalse(content.contains(token),
                    label + " must not contain forbidden token '" + token + "'");
        }
        assertFalse(BARE_DASH_V.matcher(content).find(),
                label + " must not contain a bare -v flag token");
    }

    private static void assertNoDeletionAdjacentToPreservedNames(String content, String label) {
        content.lines().forEach(line -> {
            boolean deletionLike = line.contains("rm ") || line.contains("-delete");
            if (deletionLike) {
                for (String preserved : PRESERVE_NAMES) {
                    assertFalse(line.contains(preserved),
                            label + " line looks like a deletion and references preserved name '"
                                    + preserved + "': " + line);
                }
            }
        });
    }

    @Test
    void stopScriptMatchesDockerComposeStopRegexAndHasNoForbiddenTokens() {
        Path stopScript = repoRoot().resolve("scripts/stack-stop.sh");
        assumeTrue(Files.exists(stopScript), "scripts/stack-stop.sh does not exist yet");
        String stopContent = read(stopScript);
        String libContent = read(repoRoot().resolve("scripts/lib/_lib.sh"));

        assertTrue(STOP_REGEX.matcher(stopContent).matches(),
                "stack-stop.sh must contain 'docker compose [-f FILE...] stop'");
        assertNoForbiddenTokens(stopContent, "stack-stop.sh");
        assertNoForbiddenTokens(libContent, "_lib.sh");
    }

    @Test
    void libHasNoForbiddenTokensNow() {
        String libContent = read(repoRoot().resolve("scripts/lib/_lib.sh"));
        assertNoForbiddenTokens(libContent, "_lib.sh");
    }

    @Test
    void cleanStartScriptHasNoBroadDeletionOrForbiddenTargets() {
        Path cleanStart = repoRoot().resolve("scripts/stack-clean-start.sh");
        assumeTrue(Files.exists(cleanStart), "scripts/stack-clean-start.sh does not exist yet");
        String content = read(cleanStart);

        assertFalse(content.contains("docker compose down"), "clean-start must not call docker compose down");
        assertFalse(content.contains("docker volume rm"), "clean-start must not call docker volume rm");
        assertFalse(content.contains("--volumes"), "clean-start must not use --volumes");
        assertFalse(BARE_DASH_V.matcher(content).find(), "clean-start must not use a bare -v flag");
        content.lines().forEach(line -> {
            String lower = line.toLowerCase();
            if (lower.contains("docker compose")) {
                assertFalse(lower.contains("postgres"),
                        "clean-start must never target postgres in a docker compose command: " + line);
                assertFalse(lower.contains("keycloak"),
                        "clean-start must never target keycloak in a docker compose command: " + line);
            }
        });
        assertNoDeletionAdjacentToPreservedNames(content, "stack-clean-start.sh");
    }

    @Test
    void resetRoutineHasExactClearAndPreserveDirLines() {
        String content = read(repoRoot().resolve("scripts/lib/reset-workflow-definitions.sh"));
        assertTrue(content.lines().anyMatch(l -> l.trim().equals("CLEAR_DIRS=\"workflows workflow-groups\"")),
                "reset routine must define CLEAR_DIRS exactly");
        assertTrue(content.lines().anyMatch(l -> l.trim().equals(
                        "PRESERVE_DIRS=\"agents subagents skills decision-logs approval-decisions\"")),
                "reset routine must define PRESERVE_DIRS exactly");
        assertFalse(content.contains("rm -rf"), "reset routine must never use rm -rf");
        assertTrue(content.contains("-exec rm -f {} +"), "reset routine must use the whitelisted deletion form");
        assertNoDeletionAdjacentToPreservedNames(content, "reset-workflow-definitions.sh");
    }

    @Test
    void noScriptHardcodesTheWrongDefinitionsPath() throws IOException {
        String forbiddenPath = "/tmp/embabel-workflow-definitions";
        Path scriptsDir = repoRoot().resolve("scripts");
        try (Stream<Path> walk = Files.walk(scriptsDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String content = read(p);
                assertFalse(content.contains(forbiddenPath),
                        p + " must not hardcode " + forbiddenPath);
            });
        }
    }

    @Test
    void securityConfigFilesStillHaveTheirExistingPermitAllGuard() {
        Path aiControlConfig = repoRoot().resolve(
                "services/ai-control-service/src/main/java/nl/metafactory/aicontrol/config/SecurityConfig.java");
        Path embabelConfig = repoRoot().resolve(
                "services/embabel-agent-service/src/main/java/nl/metafactory/agents/config/SecurityConfig.java");

        for (Path p : List.of(aiControlConfig, embabelConfig)) {
            if (!Files.exists(p)) {
                fail("Expected SecurityConfig.java to exist at " + p);
            }
            String content = read(p);
            assertTrue(content.contains("/actuator/health"),
                    p + " must still permit /actuator/health");
            assertTrue(content.contains("permitAll"),
                    p + " must still contain a permitAll guard");
        }
    }

    @Test
    void entryPointScriptsHaveCorrectShebangAndStrictModeWhenPresent() {
        List<String> entryPoints = List.of(
                "scripts/stack-start.sh", "scripts/stack-stop.sh", "scripts/stack-clean-start.sh");
        for (String rel : entryPoints) {
            Path p = repoRoot().resolve(rel);
            if (!Files.exists(p)) {
                continue;
            }
            String content = read(p);
            assertTrue(content.lines().findFirst().orElse("").equals("#!/usr/bin/env bash"),
                    rel + " must start with #!/usr/bin/env bash");
            assertTrue(content.contains("set -euo pipefail"), rel + " must use set -euo pipefail");
            assertTrue(Files.isExecutable(p), rel + " must be executable");
        }
        Path lib = repoRoot().resolve("scripts/lib/_lib.sh");
        String libContent = read(lib);
        assertTrue(libContent.lines().findFirst().orElse("").equals("#!/usr/bin/env bash"),
                "_lib.sh must start with #!/usr/bin/env bash");
        assertTrue(libContent.contains("set -euo pipefail"), "_lib.sh must use set -euo pipefail");
    }

    @Test
    void resetRoutineHasPosixShebangAndStrictMode() {
        String content = read(repoRoot().resolve("scripts/lib/reset-workflow-definitions.sh"));
        assertTrue(content.lines().findFirst().orElse("").equals("#!/bin/sh"),
                "reset routine must start with #!/bin/sh");
        assertTrue(content.contains("set -e"), "reset routine must use set -e");
    }
}
