package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectHermesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class ProjectHermesConfigRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectHermesConfigRepository repository;

    @BeforeEach
    void clearData() {
        repository.deleteAll();
        projectRepository.deleteAll();
        em.flush();
    }

    @Test
    void savesAndFindsActiveConfigByProjectId() {
        var project = projectRepository.saveAndFlush(newProject());
        repository.saveAndFlush(config(project.getId(), (short) 1, "wf-1"));
        em.clear();

        var found = repository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);

        assertThat(found).isPresent();
        assertThat(found.get().getWorkflowId()).isEqualTo("wf-1");
    }

    @Test
    void doesNotReturnInactiveConfig() {
        var project = projectRepository.saveAndFlush(newProject());
        repository.saveAndFlush(config(project.getId(), (short) 0, "wf-1"));
        em.clear();

        var found = repository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);

        assertThat(found).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoConfigExists() {
        var project = projectRepository.saveAndFlush(newProject());

        var found = repository.findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);

        assertThat(found).isEmpty();
    }

    @Test
    void setsCreatedAtAndUpdatedAtOnPersist() {
        var project = projectRepository.saveAndFlush(newProject());
        var saved = repository.saveAndFlush(config(project.getId(), (short) 1, "wf-1"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEnabled()).isEqualTo((short) 1);
    }

    private Project newProject() {
        var p = new Project();
        p.setName("Hermes Test Project");
        p.setActive((short) 1);
        return p;
    }

    private ProjectHermesConfig config(UUID projectId, short active, String workflowId) {
        var c = new ProjectHermesConfig();
        c.setProjectId(projectId);
        c.setActive(active);
        c.setEnabled((short) 1);
        c.setEndpointUrl("https://hermes.example.com/signals");
        c.setSignalType("issue.created");
        c.setWorkflowId(workflowId);
        return c;
    }
}
