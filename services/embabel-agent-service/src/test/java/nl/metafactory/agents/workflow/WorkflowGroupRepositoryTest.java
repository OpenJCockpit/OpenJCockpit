package nl.metafactory.agents.workflow;

import nl.metafactory.agents.config.WorkflowDefinitionProperties;
import nl.metafactory.agents.workflow.model.WorkflowGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGroupRepositoryTest {

    private YamlDefinitionStore store;
    private Path groupsDir;
    private WorkflowGroupRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var properties = new WorkflowDefinitionProperties();
        properties.setPath(tempDir.toString());
        store = new YamlDefinitionStore();
        groupsDir = tempDir.resolve("workflow-groups");
        repository = new WorkflowGroupRepository(properties, store);
    }

    @Test
    void findAllReturnsEmptyWhenNoneSaved() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveThenFindByIdRoundTrips() {
        var group = new WorkflowGroup("wg-1", "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");

        repository.save(group);

        assertThat(repository.findById("wg-1")).contains(group);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    void deleteByIdRemovesTheGroup() {
        repository.save(new WorkflowGroup("wg-2", "Name", "desc", null));

        assertThat(repository.deleteById("wg-2")).isTrue();
        assertThat(repository.findById("wg-2")).isEmpty();
    }

    @Test
    void saveGeneratesIdWhenBlank() {
        var saved = repository.save(new WorkflowGroup("", "No id yet", "desc", null));

        assertThat(saved.id()).startsWith("wg-");
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void saveGeneratesIdWhenNull() {
        var saved = repository.save(new WorkflowGroup(null, "No id yet", "desc", null));

        assertThat(saved.id()).startsWith("wg-");
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void findAllRepairsGroupStoredWithoutId() {
        var legacy = new WorkflowGroup("", "Legacy group", "desc", null);
        store.save(groupsDir, "legacy", legacy);

        var all = repository.findAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).id()).startsWith("wg-");
        assertThat(store.find(groupsDir, "legacy", WorkflowGroup.class)).isEmpty();
        assertThat(repository.findById(all.get(0).id())).isPresent();
    }

    @Test
    void findByIdRepairsGroupStoredWithoutIdUnderMatchingKey() {
        var legacy = new WorkflowGroup(null, "Legacy group", "desc", null);
        store.save(groupsDir, "legacy", legacy);

        var found = repository.findById("legacy");

        assertThat(found).isPresent();
        assertThat(found.get().id()).startsWith("wg-");
    }

    @Test
    void findAllSkipsAMalformedGroupFileAndReturnsTheRemainingValidOnes() throws java.io.IOException {
        var g1 = new WorkflowGroup("wg-1", "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
        var g2 = new WorkflowGroup("wg-2", "Offboarding flows", "Workflows around customer offboarding", "Noordzee Logistics");
        repository.save(g1);
        repository.save(g2);
        java.nio.file.Files.createDirectories(groupsDir);
        java.nio.file.Files.writeString(groupsDir.resolve("zz-broken.yaml"), "- not\n- an\n- object\n");

        assertThat(repository.findAll()).hasSize(2);
    }
}
