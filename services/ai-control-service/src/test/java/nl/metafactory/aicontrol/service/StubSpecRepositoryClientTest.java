package nl.metafactory.aicontrol.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StubSpecRepositoryClientTest {

    private static final String SPEC_REPO_PATH = "../../infrastructure/spec-repository-stub";

    @Test
    void listSpecsReadsFilesFromNoordzeeLogistics() {
        var client = new StubSpecRepositoryClient(SPEC_REPO_PATH);
        var specs = client.listSpecs("noordzee-logistics");
        assertThat(specs).hasSize(3);
        assertThat(specs.get(0).selected()).isTrue();
        assertThat(specs.stream().filter(s -> !s.selected()).count()).isEqualTo(2);
        assertThat(specs.get(0).content()).isNotBlank();
        assertThat(specs.get(0).status()).isEqualTo("Active");
        assertThat(specs.get(1).status()).isEqualTo("Open");
    }

    @Test
    void listSpecsPopulatesRepositoryUrlFromReposJson() {
        var client = new StubSpecRepositoryClient(SPEC_REPO_PATH);
        var specs = client.listSpecs("noordzee-logistics");
        var byName = specs.stream().collect(java.util.stream.Collectors.toMap(
                s -> s.fileName(), s -> s));
        assertThat(byName.get("pricing-rules.spec.md").repositoryUrl())
                .isEqualTo("https://github.com/noordzee-logistics/pricing-engine");
        assertThat(byName.get("approval-flow.spec.md").repositoryUrl())
                .isEqualTo("https://github.com/noordzee-logistics/approval-service");
        assertThat(byName.get("customer-profile-update.spec.md").repositoryUrl())
                .isEqualTo("https://github.com/noordzee-logistics/customer-service");
    }

    @Test
    void listSpecsUsesEmptyRepositoryUrlWhenReposJsonAbsent(@TempDir Path tempDir) throws IOException {
        var specsDir = tempDir.resolve("customers/test/specs");
        Files.createDirectories(specsDir);
        Files.writeString(specsDir.resolve("test.spec.md"), "# content");

        var client = new StubSpecRepositoryClient(tempDir.toString());
        var specs = client.listSpecs("test");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).repositoryUrl()).isEmpty();
    }

    @Test
    void listSpecsReturnsEmptyListForUnknownCustomer() {
        var client = new StubSpecRepositoryClient(SPEC_REPO_PATH);
        assertThat(client.listSpecs("unknown-customer")).isEmpty();
    }

    @Test
    void listSpecsReturnsSpecsEvenWhenReposJsonIsInvalid(@TempDir Path tempDir) throws IOException {
        var specsDir = tempDir.resolve("customers/test/specs");
        Files.createDirectories(specsDir);
        Files.writeString(specsDir.resolve("a.spec.md"), "# content");
        Files.writeString(tempDir.resolve("customers/test/repos.json"), "not valid json {{{");

        var client = new StubSpecRepositoryClient(tempDir.toString());
        var specs = client.listSpecs("test");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).repositoryUrl()).isEmpty();
    }

    @Test
    void listSpecsThrowsUncheckedIOExceptionOnReadFailure(@TempDir Path tempDir) throws IOException {
        var specsDir = tempDir.resolve("customers/test/specs");
        Files.createDirectories(specsDir);
        var specFile = specsDir.resolve("test.spec.md");
        Files.writeString(specFile, "content");
        assumeTrue(specFile.toFile().setReadable(false), "Cannot change file permissions on this system");

        var client = new StubSpecRepositoryClient(tempDir.toString());
        assertThatThrownBy(() -> client.listSpecs("test"))
                .isInstanceOf(java.io.UncheckedIOException.class);

        specFile.toFile().setReadable(true);
    }
}
