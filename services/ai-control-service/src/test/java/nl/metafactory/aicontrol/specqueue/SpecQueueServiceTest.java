package nl.metafactory.aicontrol.specqueue;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecQueueEnqueueRequest;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.GitWorkspaceException;
import nl.metafactory.aicontrol.service.ProjectSpecService;
import nl.metafactory.aicontrol.specqueue.app.CurrentActor;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueDtoMapper;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueService;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueUserTransitions;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator.ValidatedWorkflow;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemPullRequestRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Collaborator-mocked service tests for the paths a real database cannot reach deterministically. */
class SpecQueueServiceTest {

    private final UUID projectId = UUID.randomUUID();
    private final CurrentActor actor = new CurrentActor("sub", "alice");

    private ProjectRepository projects;
    private SpecQueueRepository queues;
    private SpecQueueItemRepository items;
    private SpecQueueItemPullRequestRepository pullRequests;
    private SpecQueueUserTransitions transitions;
    private WorkflowBindingValidator validator;
    private ProjectSpecService specService;
    private EmbabelAgentClient embabel;
    private SpecQueueService service;
    private Project project;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        queues = mock(SpecQueueRepository.class);
        items = mock(SpecQueueItemRepository.class);
        pullRequests = mock(SpecQueueItemPullRequestRepository.class);
        transitions = mock(SpecQueueUserTransitions.class);
        validator = mock(WorkflowBindingValidator.class);
        specService = mock(ProjectSpecService.class);
        embabel = mock(EmbabelAgentClient.class);
        var props = new SpecQueueProperties();
        service = new SpecQueueService(projects, queues, items, pullRequests, transitions, validator,
                specService, embabel, new SpecQueueDtoMapper(props), props);
        project = new Project();
        project.setName("proj");
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(validator.validate(any(), any())).thenReturn(new ValidatedWorkflow("wf", "WF"));
    }

    private SpecQueueItem item() {
        return new SpecQueueItem(projectId, "a.md", "wf", "WF", false, 1, "sub", "alice", Instant.now());
    }

    private static Code codeOf(Runnable r) {
        try {
            r.run();
        } catch (SpecQueueException e) {
            return e.getCode();
        }
        return null;
    }

    private Code enqueueCode() {
        return codeOf(() -> service.enqueue(projectId, new SpecQueueEnqueueRequest("a.md", "wf", false), actor));
    }

    @Test
    void enqueueRefusesInactiveProject() {
        project.setActive((short) 0);
        assertThat(enqueueCode()).isEqualTo(Code.PROJECT_INACTIVE);
    }

    @Test
    void listingFailuresAreMappedToCodes() {
        var cases = List.of(
                new Object[]{GitWorkspaceJobErrorCode.NO_SELECTED_PROJECT, Code.PROJECT_NOT_FOUND},
                new Object[]{GitWorkspaceJobErrorCode.PROJECT_NOT_FOUND, Code.PROJECT_NOT_FOUND},
                new Object[]{GitWorkspaceJobErrorCode.PROJECT_INACTIVE, Code.PROJECT_INACTIVE},
                new Object[]{GitWorkspaceJobErrorCode.GIT_URL_MISSING, Code.GIT_URL_MISSING},
                new Object[]{GitWorkspaceJobErrorCode.GIT_AUTH_FAILED, Code.GIT_AUTH_FAILED},
                new Object[]{GitWorkspaceJobErrorCode.GIT_CLONE_FAILED, Code.SPEC_LISTING_FAILED});
        for (Object[] c : cases) {
            org.mockito.Mockito.doThrow(new GitWorkspaceException((GitWorkspaceJobErrorCode) c[0], "x"))
                    .when(specService).listSpecFiles(projectId);
            assertThat(enqueueCode()).isEqualTo(c[1]);
        }
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(specService).listSpecFiles(projectId);
        assertThat(enqueueCode()).isEqualTo(Code.SPEC_LISTING_FAILED);
    }

    @Test
    void constraintViolationAfterTransactionIsExplained() {
        when(specService.listSpecFiles(projectId)).thenReturn(List.of(
                new SpecFile("a.md", "a.md", null, null, null, false, null, null)));
        when(transitions.appendItem(eq(projectId), any(), any(), eq(0), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));

        when(projects.existsById(projectId)).thenReturn(false);
        assertThat(enqueueCode()).isEqualTo(Code.PROJECT_NOT_FOUND);

        when(projects.existsById(projectId)).thenReturn(true);
        when(items.findByProjectIdAndOpenSpecKey(projectId, "a.md")).thenReturn(Optional.of(item()));
        assertThat(enqueueCode()).isEqualTo(Code.DUPLICATE_SPEC_FILE);

        when(items.findByProjectIdAndOpenSpecKey(projectId, "a.md")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enqueue(projectId, new SpecQueueEnqueueRequest("a.md", "wf", false), actor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockContentionBecomesQueueBusy() {
        when(transitions.retry(any(), any(), any())).thenThrow(new CannotAcquireLockException("lock"));
        assertThat(codeOf(() -> service.retry(projectId, UUID.randomUUID(), actor))).isEqualTo(Code.QUEUE_BUSY);
        when(transitions.skip(any(), any(), any())).thenThrow(new OptimisticLockingFailureException("version"));
        assertThat(codeOf(() -> service.skip(projectId, UUID.randomUUID(), actor))).isEqualTo(Code.QUEUE_BUSY);
    }

    @Test
    void updateRefusesNewAutoMergeWhenNotAllowed() {
        SpecQueueItem queued = item();
        when(items.findByIdAndProjectId(queued.getId(), projectId)).thenReturn(Optional.of(queued));
        when(queues.findById(projectId)).thenReturn(Optional.empty());
        var request = new nl.metafactory.aicontrol.model.SpecQueueItemUpdateRequest(null, true);
        assertThat(codeOf(() -> service.updateItem(projectId, queued.getId(), request, actor)))
                .isEqualTo(Code.AUTO_MERGE_NOT_ALLOWED);
    }

    @Test
    void cancelFailsWithItemBusyWhenRunnerMovedTheItemOn() {
        SpecQueueItem running = item();
        running.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        when(items.findByIdAndProjectId(running.getId(), projectId)).thenReturn(Optional.of(running));
        when(transitions.beginCancel(projectId, running.getId())).thenReturn("run-1");
        when(transitions.completeCancel(eq(projectId), eq(running.getId()), any())).thenReturn(running);
        assertThat(codeOf(() -> service.removeItem(projectId, running.getId(), actor))).isEqualTo(Code.ITEM_BUSY);
    }

    @Test
    void queuedItemMissingFromTheRankListGetsNoRank() {
        SpecQueueItem queued = item();
        when(transitions.retry(projectId, queued.getId(), actor)).thenReturn(queued);
        when(items.findByProjectIdAndStatusInOrderByPositionAsc(eq(projectId), any())).thenReturn(List.of());
        assertThat(service.retry(projectId, queued.getId(), actor).position()).isNull();
        org.mockito.Mockito.verify(embabel, org.mockito.Mockito.never()).stopAgentRun(any());
    }
}
