package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.ExecResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowContainerServiceTest {

    private ContainerRuntime runtime;
    private AgenticWorkflowProperties properties;
    private WorkflowContainerService service;

    @BeforeEach
    void setUp() {
        runtime = mock(ContainerRuntime.class);
        properties = new AgenticWorkflowProperties();
        properties.setCleanupEnabled(true);
        service = new WorkflowContainerService(runtime, properties);
    }

    @Test
    void createWorkflowContainerDelegatesToRuntime() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(runtime.createContainer(any(), any(), any(), anyLong(), anyLong(), any()))
                .thenReturn("container-abc");

        String id = service.createWorkflowContainer(jobId, projectId);

        assertThat(id).isEqualTo("container-abc");
        verify(runtime).createContainer(
                eq(properties.getContainer().getImage()),
                eq(jobId),
                eq(projectId),
                eq(properties.getTmpfs().getSizeMb()),
                eq(properties.getContainer().getMemoryMb() * 1024 * 1024),
                argThat(labels -> "agentic-workflow".equals(labels.get("app"))
                        && jobId.toString().equals(labels.get("jobId"))
                        && projectId.toString().equals(labels.get("projectId")))
        );
    }

    @Test
    void createWorkflowContainerReturnsNullWhenDisabled() {
        properties.getContainer().setEnabled(false);
        String id = service.createWorkflowContainer(UUID.randomUUID(), UUID.randomUUID());
        assertThat(id).isNull();
        verifyNoInteractions(runtime);
    }

    @Test
    void startContainerDelegates() {
        service.startContainer("cid-123");
        verify(runtime).startContainer("cid-123");
    }

    @Test
    void startContainerNoopOnNull() {
        service.startContainer(null);
        verifyNoInteractions(runtime);
    }

    @Test
    void executeCommandInContainerReturnsResult() {
        when(runtime.executeInContainer(eq("cid"), any(), any()))
                .thenReturn(new ExecResult(0, "ok", ""));

        ExecResult result = service.executeCommandInContainer(
                "cid", List.of("echo", "hello"), Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.stdout()).isEqualTo("ok");
    }

    @Test
    void executeCommandInContainerLogsWarningOnFailure() {
        when(runtime.executeInContainer(eq("cid"), any(), any()))
                .thenReturn(new ExecResult(1, "", "boom"));

        ExecResult result = service.executeCommandInContainer(
                "cid", List.of("false"), Duration.ofSeconds(5));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).isEqualTo("boom");
    }

    @Test
    void executeCommandInContainerReturnsSuccessResultWhenContainerIdNull() {
        ExecResult result = service.executeCommandInContainer(
                null, List.of("echo"), Duration.ofSeconds(1));
        assertThat(result.success()).isTrue();
        verifyNoInteractions(runtime);
    }

    @Test
    void cleanupContainerStopsAndRemoves() {
        service.cleanupContainer("cid-xyz");
        verify(runtime).stopContainer("cid-xyz");
        verify(runtime).removeContainer("cid-xyz");
    }

    @Test
    void cleanupContainerNoopOnNull() {
        service.cleanupContainer(null);
        verifyNoInteractions(runtime);
    }

    @Test
    void cleanupContainerNoopWhenCleanupDisabled() {
        properties.setCleanupEnabled(false);
        service.cleanupContainer("cid-xyz");
        verifyNoInteractions(runtime);
    }

    @Test
    void cleanupContainerContinuesOnStopException() {
        doThrow(new RuntimeException("stop failed")).when(runtime).stopContainer(any());
        service.cleanupContainer("cid-err");
        verify(runtime).removeContainer("cid-err");
    }

    @Test
    void cleanupContainerContinuesOnRemoveException() {
        doThrow(new RuntimeException("remove failed")).when(runtime).removeContainer(any());
        service.cleanupContainer("cid-err");
        verify(runtime).stopContainer("cid-err");
    }

    @Test
    void cleanupOrphanedContainersStopsFound() {
        when(runtime.findContainersByLabel("app", "agentic-workflow"))
                .thenReturn(List.of("orphan-1", "orphan-2"));

        service.cleanupOrphanedContainers();

        verify(runtime).stopContainer("orphan-1");
        verify(runtime).removeContainer("orphan-1");
        verify(runtime).stopContainer("orphan-2");
        verify(runtime).removeContainer("orphan-2");
    }

    @Test
    void cleanupOrphanedContainersNoopWhenDisabled() {
        properties.setCleanupEnabled(false);
        service.cleanupOrphanedContainers();
        verifyNoInteractions(runtime);
    }

    @Test
    void cleanupOrphanedContainersCatchesRuntimeException() {
        doThrow(new RuntimeException("docker down")).when(runtime).findContainersByLabel(any(), any());
        service.cleanupOrphanedContainers();
        verify(runtime).findContainersByLabel(any(), any());
    }
}