package nl.metafactory.aicontrol.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Permanent regression guard for defect D1 (framework-upgrade QA gate,
 * docs/delivery/upgrade-embabel-spring-ai-spring-boot-latest/06-qa-report.md): a
 * {@link LinkageError} (specifically {@code NoSuchFieldError:
 * PropertyNamingStrategy.SNAKE_CASE}) used to be thrown at runtime from
 * {@code org.kohsuke.github.GitHubClient}'s static initializer whenever
 * {@link GitAdapterConfig#gitHubPort()} was actually invoked, because
 * {@code org.kohsuke:github-api}'s transitive {@code jackson-databind} dependency
 * resolved to an incompatible version (2.20+, which removed that field) once Spring
 * Boot's own Jackson-2 BOM management changed. An interim fix pinned jackson-databind/
 * jackson-core to 2.19.2; the durable fix instead upgrades {@code org.kohsuke:github-api}
 * to 1.330+, whose {@code GitHubClient} uses the non-deprecated {@code PropertyNamingStrategies}
 * class (plural) and itself requires jackson-databind 2.20+, so this module's own
 * jackson-databind/jackson-core {@code dependencyManagement} pin in pom.xml is free to
 * track current releases (2.22.2 as of this writing) again.
 *
 * <p>{@link GitAdapterConfig} is excluded from the JaCoCo coverage gate (see the root
 * pom.xml), so this bean's real runtime behaviour was otherwise completely untested.
 * This test constructs the real bean and performs a real, unauthenticated-equivalent
 * network call against a real, stable public GitHub repository to prove the class
 * loads and runs without the regression. It requires real network access to
 * {@code api.github.com}.
 */
class GitAdapterConfigGitHubPortIntegrationTest {

    @Test
    void gitHubPortBeanChecksRepositoryWithoutLinkageError() {
        var port = new GitAdapterConfig().gitHubPort();
        try {
            port.checkRepository("octocat", "Hello-World", "https://api.github.com",
                    "test-token-not-a-real-credential");
            // A successful call is a pass: the bean loaded and ran with no LinkageError.
        } catch (LinkageError e) {
            fail("GitHubPort bean threw a LinkageError (the D1 regression): " + e, e);
        } catch (Exception e) {
            // Any ordinary checked/unchecked exception (e.g. an IOException wrapping a
            // 401 from GitHub for the placeholder token) is acceptable: java.lang.Exception
            // and java.lang.LinkageError are disjoint types (both extend Throwable but
            // neither extends the other), so reaching this branch already proves the
            // failure was not the D1 regression (a LinkageError, caught above).
        }
    }
}
