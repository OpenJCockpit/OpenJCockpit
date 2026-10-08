package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitStatus;
import nl.metafactory.aicontrol.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class ProjectRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProjectRepository repository;

    @BeforeEach
    void clearData() {
        repository.deleteAll();
        em.flush();
    }

    @Test
    void saveSetsCreatedAtAndUpdatedAt() {
        var project = newProject("Alpha", (short) 1);
        var saved = repository.saveAndFlush(project);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void findAllByActiveOrderByNameAscReturnsOnlyActive() {
        repository.saveAndFlush(newProject("Beta", (short) 1));
        repository.saveAndFlush(newProject("Alpha", (short) 1));
        repository.saveAndFlush(newProject("Inactive", (short) 0));

        var active = repository.findAllByActiveOrderByNameAsc((short) 1);

        assertThat(active).hasSize(2);
        assertThat(active.get(0).getName()).isEqualTo("Alpha");
        assertThat(active.get(1).getName()).isEqualTo("Beta");
    }

    @Test
    void findAllByOrderByNameAscReturnsAll() {
        repository.saveAndFlush(newProject("Z", (short) 1));
        repository.saveAndFlush(newProject("A", (short) 0));

        var all = repository.findAllByOrderByNameAsc();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getName()).isEqualTo("A");
    }

    @Test
    void updateChangesUpdatedAt() throws InterruptedException {
        var project = repository.saveAndFlush(newProject("Gamma", (short) 1));
        var originalUpdatedAt = project.getUpdatedAt();

        Thread.sleep(10);
        project.setName("Gamma Renamed");
        var updated = repository.saveAndFlush(project);

        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        assertThat(updated.getName()).isEqualTo("Gamma Renamed");
    }

    @Test
    void softDeleteSetsActiveToZero() {
        var project = repository.saveAndFlush(newProject("Delta", (short) 1));
        project.setActive((short) 0);
        var updated = repository.saveAndFlush(project);

        assertThat(updated.getActive()).isZero();
        assertThat(repository.findAllByActiveOrderByNameAsc((short) 1)).isEmpty();
    }

    @Test
    void allFieldsArePersistedAndRetrieved() {
        var project = newProject("Epsilon", (short) 1);
        project.setCustomerId("epsilon-corp");
        project.setGitUrl("https://github.com/epsilon");
        project.setDescription("A test project");
        project.setDefaultBranch("develop");
        project.setEnvironment("staging");
        project.setOwner("Alice");

        var saved = repository.saveAndFlush(project);
        em.clear();
        var found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getCustomerId()).isEqualTo("epsilon-corp");
        assertThat(found.getGitUrl()).isEqualTo("https://github.com/epsilon");
        assertThat(found.getDescription()).isEqualTo("A test project");
        assertThat(found.getDefaultBranch()).isEqualTo("develop");
        assertThat(found.getEnvironment()).isEqualTo("staging");
        assertThat(found.getOwner()).isEqualTo("Alice");
    }

    @Test
    void newProjectDefaultsToZeroAndGitStatusToUnknown() {
        var project = repository.saveAndFlush(newProject("Zeta", (short) 1));
        em.clear();
        var found = repository.findById(project.getId()).orElseThrow();

        assertThat(found.getNewProject()).isEqualTo((short) 0);
        assertThat(found.getGitStatus()).isEqualTo(GitStatus.UNKNOWN);
        assertThat(found.getGitStatusCheckedAt()).isNull();
        assertThat(found.getGitStatusMessage()).isNull();
    }

    @Test
    void gitStatusFieldsArePersisted() {
        var project = newProject("Eta", (short) 1);
        project.setGitStatus(GitStatus.ACCESSIBLE);
        project.setGitStatusMessage("Reachable");
        project.setNewProject((short) 1);

        var saved = repository.saveAndFlush(project);
        em.clear();
        var found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getGitStatus()).isEqualTo(GitStatus.ACCESSIBLE);
        assertThat(found.getGitStatusMessage()).isEqualTo("Reachable");
        assertThat(found.getNewProject()).isEqualTo((short) 1);
    }

    private Project newProject(String name, short active) {
        var p = new Project();
        p.setName(name);
        p.setActive(active);
        return p;
    }
}
