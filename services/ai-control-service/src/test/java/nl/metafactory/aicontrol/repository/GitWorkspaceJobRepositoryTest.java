package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class GitWorkspaceJobRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GitWorkspaceJobRepository repository;

    @Autowired
    private ProjectRepository projectRepository;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        var project = new nl.metafactory.aicontrol.model.Project();
        project.setName("Test Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo");
        var saved = projectRepository.saveAndFlush(project);
        projectId = saved.getId();
    }

    @Test
    void savePersistsJobWithTimestamps() {
        var job = newJob(projectId, "spec-001");
        var saved = repository.saveAndFlush(job);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(GitWorkspaceJobStatus.CREATED);
    }

    @Test
    void findAllByProjectIdOrderByCreatedAtDescReturnsMostRecentFirst() throws InterruptedException {
        var job1 = repository.saveAndFlush(newJob(projectId, "spec-a"));
        Thread.sleep(10);
        var job2 = repository.saveAndFlush(newJob(projectId, "spec-b"));

        var results = repository.findAllByProjectIdOrderByCreatedAtDesc(projectId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSpecFileRef()).isEqualTo("spec-b");
        assertThat(results.get(1).getSpecFileRef()).isEqualTo("spec-a");
    }

    @Test
    void findAllByProjectIdReturnsOnlyJobsForThatProject() {
        var otherProject = new nl.metafactory.aicontrol.model.Project();
        otherProject.setName("Other");
        otherProject.setActive((short) 1);
        otherProject.setGitUrl("https://github.com/other/repo");
        UUID otherId = projectRepository.saveAndFlush(otherProject).getId();

        repository.saveAndFlush(newJob(projectId, "spec-mine"));
        repository.saveAndFlush(newJob(otherId, "spec-other"));

        var results = repository.findAllByProjectIdOrderByCreatedAtDesc(projectId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSpecFileRef()).isEqualTo("spec-mine");
    }

    @Test
    void findAllByStatusAndCreatedAtBeforeFiltersCorrectly() throws InterruptedException {
        var oldJob = newJob(projectId, "old-spec");
        repository.saveAndFlush(oldJob);

        Thread.sleep(10);
        Instant cutoff = Instant.now();

        var results = repository.findAllByStatusAndCreatedAtBefore(
                GitWorkspaceJobStatus.CREATED, cutoff);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSpecFileRef()).isEqualTo("old-spec");
    }

    @Test
    void findAllByStatusAndCreatedAtBeforeExcludesNewerJobs() {
        Instant cutoff = Instant.now();
        repository.saveAndFlush(newJob(projectId, "new-spec"));

        var results = repository.findAllByStatusAndCreatedAtBefore(
                GitWorkspaceJobStatus.CREATED, cutoff);

        assertThat(results).isEmpty();
    }

    @Test
    void updateChangesStatus() {
        var job = repository.saveAndFlush(newJob(projectId, "spec-x"));
        job.setStatus(GitWorkspaceJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        var updated = repository.saveAndFlush(job);

        em.clear();
        var found = repository.findById(updated.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(GitWorkspaceJobStatus.COMPLETED);
        assertThat(found.getCompletedAt()).isNotNull();
    }

    private GitWorkspaceJob newJob(UUID projectId, String specFileRef) {
        var job = new GitWorkspaceJob();
        job.setProjectId(projectId);
        job.setSpecFileRef(specFileRef);
        job.setStatus(GitWorkspaceJobStatus.CREATED);
        job.setBaseBranch("main");
        return job;
    }
}