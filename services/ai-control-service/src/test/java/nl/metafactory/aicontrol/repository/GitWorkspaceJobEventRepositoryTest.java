package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
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
class GitWorkspaceJobEventRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GitWorkspaceJobEventRepository repository;

    @Autowired
    private GitWorkspaceJobRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private UUID jobId;

    @BeforeEach
    void setUp() {
        var project = new nl.metafactory.aicontrol.model.Project();
        project.setName("Test Project");
        project.setActive((short) 1);
        project.setGitUrl("https://github.com/org/repo");
        var savedProject = projectRepository.saveAndFlush(project);

        var job = new GitWorkspaceJob();
        job.setProjectId(savedProject.getId());
        job.setSpecFileRef("spec-001");
        job.setStatus(GitWorkspaceJobStatus.CREATED);
        job.setBaseBranch("main");
        var savedJob = jobRepository.saveAndFlush(job);
        jobId = savedJob.getId();
    }

    @Test
    void saveEventPersistsWithTimestamp() {
        var event = new GitWorkspaceJobEvent(jobId, "JOB_CREATED", "Job created");
        var saved = repository.saveAndFlush(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo("JOB_CREATED");
        assertThat(saved.getMessage()).isEqualTo("Job created");
    }

    @Test
    void findAllByJobIdOrderByCreatedAtAscReturnsEventsInOrder() throws InterruptedException {
        repository.saveAndFlush(new GitWorkspaceJobEvent(jobId, "FIRST", "First event"));
        Thread.sleep(10);
        repository.saveAndFlush(new GitWorkspaceJobEvent(jobId, "SECOND", "Second event"));

        var events = repository.findAllByJobIdOrderByCreatedAtAsc(jobId);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("FIRST");
        assertThat(events.get(1).getEventType()).isEqualTo("SECOND");
    }

    @Test
    void findAllByJobIdReturnsOnlyEventsForThatJob() {
        var otherProject = new nl.metafactory.aicontrol.model.Project();
        otherProject.setName("Other");
        otherProject.setActive((short) 1);
        otherProject.setGitUrl("https://github.com/other/repo");
        var otherSavedProject = projectRepository.saveAndFlush(otherProject);

        var otherJob = new GitWorkspaceJob();
        otherJob.setProjectId(otherSavedProject.getId());
        otherJob.setSpecFileRef("other-spec");
        otherJob.setStatus(GitWorkspaceJobStatus.CREATED);
        otherJob.setBaseBranch("main");
        UUID otherJobId = jobRepository.saveAndFlush(otherJob).getId();

        repository.saveAndFlush(new GitWorkspaceJobEvent(jobId, "MY_EVENT", "mine"));
        repository.saveAndFlush(new GitWorkspaceJobEvent(otherJobId, "OTHER_EVENT", "other"));

        var events = repository.findAllByJobIdOrderByCreatedAtAsc(jobId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("MY_EVENT");
    }

    @Test
    void findAllByJobIdReturnsEmptyListForUnknownJob() {
        var events = repository.findAllByJobIdOrderByCreatedAtAsc(UUID.randomUUID());
        assertThat(events).isEmpty();
    }

    @Test
    void eventWithNullMessageIsPersisted() {
        var event = new GitWorkspaceJobEvent(jobId, "STATUS_CHANGE", null);
        var saved = repository.saveAndFlush(event);

        em.clear();
        var found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMessage()).isNull();
    }
}