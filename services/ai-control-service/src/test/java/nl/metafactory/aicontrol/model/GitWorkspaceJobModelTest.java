package nl.metafactory.aicontrol.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GitWorkspaceJobModelTest {

    @Test
    void containerIdAndWorkspacePathGettersAndSetters() {
        var job = new GitWorkspaceJob();

        job.setContainerId("cid-123");
        job.setWorkspacePath("/tmp/job-1");

        assertThat(job.getContainerId()).isEqualTo("cid-123");
        assertThat(job.getWorkspacePath()).isEqualTo("/tmp/job-1");
    }

    @Test
    void eventSettersAndPrePersist() {
        var jobId = UUID.randomUUID();
        var event = new GitWorkspaceJobEvent();

        event.setJobId(jobId);
        event.setEventType("JOB_CREATED");
        event.setMessage("Workflow job created");

        assertThat(event.getJobId()).isEqualTo(jobId);
        assertThat(event.getEventType()).isEqualTo("JOB_CREATED");
        assertThat(event.getMessage()).isEqualTo("Workflow job created");
        assertThat(event.getCreatedAt()).isNull();

        event.prePersist();

        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersistDoesNotOverrideExistingCreatedAt() {
        var event = new GitWorkspaceJobEvent(UUID.randomUUID(), "JOB_CREATED", "msg");
        var originalCreatedAt = event.getCreatedAt();

        event.prePersist();

        assertThat(event.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
}
