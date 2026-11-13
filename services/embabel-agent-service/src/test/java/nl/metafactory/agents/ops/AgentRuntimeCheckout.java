package nl.metafactory.agents.ops;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The embabel-agent-runtime lives in its own repository (OpenJCockpit-Agent-Runtime). Contract tests that
 * compare this repository's wiring with the runtime's files read them from a checkout of it: the directory in
 * AGENT_RUNTIME_DIR (set by backend CI, which then requires it) or, locally, the sibling checkout next to this
 * repository. Without either, those tests are skipped locally.
 */
final class AgentRuntimeCheckout {

    static final String ENV = "AGENT_RUNTIME_DIR";

    private AgentRuntimeCheckout() {
    }

    static Path root() {
        String configured = System.getenv(ENV);
        if (configured != null && !configured.isBlank()) {
            Path dir = Path.of(configured);
            assertThat(dir.resolve("embabel-agent-runtime")).as(ENV + " must point to an OpenJCockpit-Agent-Runtime checkout")
                    .isDirectory();
            return dir;
        }
        Path sibling = Path.of("../../../OpenJCockpit-Agent-Runtime");
        assumeTrue(Files.isDirectory(sibling.resolve("embabel-agent-runtime")),
                "no OpenJCockpit-Agent-Runtime checkout (set " + ENV + " or clone it next to this repository)");
        return sibling;
    }

    static Path module() {
        return root().resolve("embabel-agent-runtime");
    }
}
