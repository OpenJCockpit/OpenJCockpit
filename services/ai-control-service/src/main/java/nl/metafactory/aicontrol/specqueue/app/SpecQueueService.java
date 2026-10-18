package nl.metafactory.aicontrol.specqueue.app;

import nl.metafactory.aicontrol.config.SpecQueueProperties;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecQueueDto;
import nl.metafactory.aicontrol.model.SpecQueueEnqueueRequest;
import nl.metafactory.aicontrol.model.SpecQueueItemDto;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.model.SpecQueueItemUpdateRequest;
import nl.metafactory.aicontrol.model.SpecQueueSettingsDto;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.GitWorkspaceException;
import nl.metafactory.aicontrol.service.ProjectSpecService;
import nl.metafactory.aicontrol.specqueue.app.SpecQueueException.Code;
import nl.metafactory.aicontrol.specqueue.app.WorkflowBindingValidator.ValidatedWorkflow;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueue;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItemPullRequest;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemPullRequestRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * User-facing queue operations. Deliberately NOT transactional: every remote call (workflow reads,
 * spec listing, stopping a run) happens outside the short transactions in
 * {@link SpecQueueUserTransitions}, so no database lock is held across remote I/O.
 */
@Service
public class SpecQueueService {

    private static final Set<SpecQueueItemStatus> LIVE = EnumSet.of(
            SpecQueueItemStatus.QUEUED, SpecQueueItemStatus.STARTING, SpecQueueItemStatus.RUNNING,
            SpecQueueItemStatus.AWAITING_MERGE, SpecQueueItemStatus.MERGING, SpecQueueItemStatus.FAILED);
    private static final Set<SpecQueueItemStatus> FINISHED = EnumSet.of(
            SpecQueueItemStatus.MERGED, SpecQueueItemStatus.COMPLETED_NO_CHANGES,
            SpecQueueItemStatus.SKIPPED, SpecQueueItemStatus.CANCELLED);

    private final ProjectRepository projects;
    private final SpecQueueRepository queues;
    private final SpecQueueItemRepository items;
    private final SpecQueueItemPullRequestRepository pullRequests;
    private final SpecQueueUserTransitions userTransitions;
    private final WorkflowBindingValidator workflowValidator;
    private final ProjectSpecService projectSpecService;
    private final EmbabelAgentClient embabelAgentClient;
    private final SpecQueueDtoMapper mapper;
    private final SpecQueueProperties properties;

    public SpecQueueService(ProjectRepository projects, SpecQueueRepository queues, SpecQueueItemRepository items,
                            SpecQueueItemPullRequestRepository pullRequests, SpecQueueUserTransitions userTransitions,
                            WorkflowBindingValidator workflowValidator, ProjectSpecService projectSpecService,
                            EmbabelAgentClient embabelAgentClient, SpecQueueDtoMapper mapper,
                            SpecQueueProperties properties) {
        this.projects = projects;
        this.queues = queues;
        this.items = items;
        this.pullRequests = pullRequests;
        this.userTransitions = userTransitions;
        this.workflowValidator = workflowValidator;
        this.projectSpecService = projectSpecService;
        this.embabelAgentClient = embabelAgentClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    public SpecQueueDto getQueue(UUID projectId) {
        requireProject(projectId);
        return buildQueueDto(projectId);
    }

    public SpecQueueItemDto enqueue(UUID projectId, SpecQueueEnqueueRequest request, CurrentActor actor) {
        requireSafeSpecFilePath(request.specFile());
        Project project = requireProject(projectId);
        if (project.getActive() != 1) {
            throw new SpecQueueException(Code.PROJECT_INACTIVE, "The project is inactive");
        }
        if (request.autoMerge() && !autoMergeAllowed(projectId)) {
            throw new SpecQueueException(Code.AUTO_MERGE_NOT_ALLOWED, "Auto-merge is not allowed for this project");
        }
        ValidatedWorkflow workflow = workflowValidator.validate(request.workflowId(), project.getName());
        SpecFile specFile = findListedSpecFile(projectId, request.specFile());
        int size = specFile.content() == null ? 0 : specFile.content().getBytes(StandardCharsets.UTF_8).length;
        try {
            SpecQueueItem item = inTransition(() ->
                    userTransitions.appendItem(projectId, request, workflow, size, actor));
            return toDto(item);
        } catch (DataIntegrityViolationException e) {
            if (!projects.existsById(projectId)) {
                throw new SpecQueueException(Code.PROJECT_NOT_FOUND, "Project not found");
            }
            if (items.findByProjectIdAndOpenSpecKey(projectId, request.specFile()).isPresent()) {
                throw new SpecQueueException(Code.DUPLICATE_SPEC_FILE, "This spec file is already queued for the project");
            }
            // e.g. a concurrent append took the same position: nothing is wrong with the request itself.
            throw new SpecQueueException(Code.QUEUE_BUSY, "The queue is busy; try again");
        }
    }

    public SpecQueueItemDto updateItem(UUID projectId, UUID itemId, SpecQueueItemUpdateRequest request,
                                       CurrentActor actor) {
        Project project = requireProject(projectId);
        SpecQueueItem item = requireItem(projectId, itemId);
        if (item.getStatus() != SpecQueueItemStatus.QUEUED) {
            throw new SpecQueueException(Code.ITEM_NOT_EDITABLE, "Only queued items can be edited");
        }
        if (Boolean.TRUE.equals(request.autoMerge()) && !item.isAutoMerge() && !autoMergeAllowed(projectId)) {
            throw new SpecQueueException(Code.AUTO_MERGE_NOT_ALLOWED, "Auto-merge is not allowed for this project");
        }
        ValidatedWorkflow workflow = request.workflowId() == null
                ? null : workflowValidator.validate(request.workflowId(), project.getName());
        return toDto(inTransition(() -> userTransitions.updateItem(projectId, itemId, request, workflow, actor)));
    }

    public SpecQueueDto reorder(UUID projectId, List<UUID> itemIds, CurrentActor actor) {
        requireProject(projectId);
        inTransition(() -> {
            userTransitions.reorder(projectId, itemIds, actor);
            return null;
        });
        return buildQueueDto(projectId);
    }

    public SpecQueueItemDto removeItem(UUID projectId, UUID itemId, CurrentActor actor) {
        requireProject(projectId);
        SpecQueueItem current = requireItem(projectId, itemId);
        if (current.getStatus() == SpecQueueItemStatus.RUNNING) {
            return toDto(cancelRunningItem(projectId, itemId, actor));
        }
        return toDto(inTransition(() -> userTransitions.removeOrCancelIdleItem(projectId, itemId, actor)));
    }

    public SpecQueueDto pause(UUID projectId, CurrentActor actor) {
        requireProject(projectId);
        inTransition(() -> {
            userTransitions.pause(projectId, actor);
            return null;
        });
        return buildQueueDto(projectId);
    }

    public SpecQueueDto resume(UUID projectId, CurrentActor actor) {
        requireProject(projectId);
        inTransition(() -> {
            userTransitions.resume(projectId, actor);
            return null;
        });
        return buildQueueDto(projectId);
    }

    public SpecQueueItemDto retry(UUID projectId, UUID itemId, CurrentActor actor) {
        requireProject(projectId);
        return toDto(inTransition(() -> userTransitions.retry(projectId, itemId, actor)));
    }

    public SpecQueueItemDto skip(UUID projectId, UUID itemId, CurrentActor actor) {
        requireProject(projectId);
        return toDto(inTransition(() -> userTransitions.skip(projectId, itemId, actor)));
    }

    public SpecQueueSettingsDto getSettings(UUID projectId) {
        requireProject(projectId);
        return mapper.toSettingsDto(queues.findById(projectId).orElse(null));
    }

    /** Role checks are the controller's job. */
    public SpecQueueSettingsDto updateSettings(UUID projectId, boolean autoMergeAllowed, CurrentActor actor) {
        requireProject(projectId);
        SpecQueue queue = inTransition(() -> userTransitions.changeAutoMergeAllowed(projectId, autoMergeAllowed, actor));
        return mapper.toSettingsDto(queue);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private SpecQueueItem cancelRunningItem(UUID projectId, UUID itemId, CurrentActor actor) {
        String runId = inTransition(() -> userTransitions.beginCancel(projectId, itemId));
        try {
            embabelAgentClient.stopAgentRun(runId);
        } catch (RuntimeException e) {
            inTransition(() -> {
                userTransitions.abortCancel(projectId, itemId);
                return null;
            });
            throw new SpecQueueException(Code.EMBABEL_UNAVAILABLE,
                    "The run could not be stopped; nothing was changed");
        }
        SpecQueueItem result = inTransition(() -> userTransitions.completeCancel(projectId, itemId, actor));
        if (result.getStatus() != SpecQueueItemStatus.CANCELLED) {
            throw new SpecQueueException(Code.ITEM_BUSY, "The item changed while it was being cancelled");
        }
        return result;
    }

    private <T> T inTransition(Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException | OptimisticLockingFailureException e) {
            throw new SpecQueueException(Code.QUEUE_BUSY, "The queue is busy; try again");
        }
    }

    private Project requireProject(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new SpecQueueException(Code.PROJECT_NOT_FOUND, "Project not found"));
    }

    private SpecQueueItem requireItem(UUID projectId, UUID itemId) {
        return items.findByIdAndProjectId(itemId, projectId)
                .orElseThrow(() -> new SpecQueueException(Code.ITEM_NOT_FOUND, "Queue item not found"));
    }

    private boolean autoMergeAllowed(UUID projectId) {
        return queues.findById(projectId).map(SpecQueue::isAutoMergeAllowed).orElse(false);
    }

    private static void requireSafeSpecFilePath(String specFile) {
        boolean unsafe = specFile == null || specFile.isBlank() || specFile.startsWith("/")
                || specFile.indexOf('\\') >= 0 || specFile.chars().anyMatch(Character::isISOControl);
        if (!unsafe) {
            for (String segment : specFile.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    unsafe = true;
                    break;
                }
            }
        }
        if (unsafe) {
            throw new SpecQueueException(Code.VALIDATION_ERROR,
                    "specFile must be a relative path inside the specs folder");
        }
    }

    /** The repository listing is the allow-list: only an exact file-name match is accepted. */
    private SpecFile findListedSpecFile(UUID projectId, String fileName) {
        List<SpecFile> listing;
        try {
            listing = projectSpecService.listSpecFiles(projectId);
        } catch (GitWorkspaceException e) {
            throw new SpecQueueException(switch (e.getErrorCode()) {
                case NO_SELECTED_PROJECT, PROJECT_NOT_FOUND -> Code.PROJECT_NOT_FOUND;
                case PROJECT_INACTIVE -> Code.PROJECT_INACTIVE;
                case GIT_URL_MISSING -> Code.GIT_URL_MISSING;
                case GIT_AUTH_FAILED -> Code.GIT_AUTH_FAILED;
                default -> Code.SPEC_LISTING_FAILED;
            }, "Spec files could not be listed");
        } catch (RuntimeException e) {
            throw new SpecQueueException(Code.SPEC_LISTING_FAILED, "Spec files could not be listed");
        }
        return listing.stream()
                .filter(f -> fileName.equals(f.fileName()))
                .findFirst()
                .orElseThrow(() -> new SpecQueueException(Code.SPEC_FILE_NOT_FOUND, "Spec file not found in the project"));
    }

    private SpecQueueDto buildQueueDto(UUID projectId) {
        SpecQueue queue = queues.findById(projectId).orElse(null);
        List<SpecQueueItem> live = items.findByProjectIdAndStatusInOrderByPositionAsc(projectId, LIVE);
        List<SpecQueueItem> queuedOnly = live.stream()
                .filter(i -> i.getStatus() == SpecQueueItemStatus.QUEUED).toList();
        List<SpecQueueItemDto> liveDtos = live.stream()
                .map(i -> mapper.toItemDto(i, rankIn(queuedOnly, i), currentRunPrUrls(i)))
                .toList();
        List<SpecQueueItemDto> finished = items.findByProjectIdAndStatusInOrderByPositionAsc(projectId, FINISHED).stream()
                .sorted(Comparator.comparing(
                        (SpecQueueItem i) -> i.getFinishedAt() != null ? i.getFinishedAt() : i.getUpdatedAt()).reversed())
                .limit(properties.getRunner().getRecentlyFinishedLimit())
                .map(i -> mapper.toItemDto(i, null, currentRunPrUrls(i)))
                .toList();
        return mapper.toQueueDto(projectId, queue, liveDtos, finished);
    }

    private SpecQueueItemDto toDto(SpecQueueItem item) {
        Integer rank = null;
        if (item.getStatus() == SpecQueueItemStatus.QUEUED) {
            rank = rankIn(items.findByProjectIdAndStatusInOrderByPositionAsc(
                    item.getProjectId(), List.of(SpecQueueItemStatus.QUEUED)), item);
        }
        return mapper.toItemDto(item, rank, currentRunPrUrls(item));
    }

    private static Integer rankIn(List<SpecQueueItem> queuedInOrder, SpecQueueItem item) {
        if (item.getStatus() != SpecQueueItemStatus.QUEUED) {
            return null;
        }
        for (int i = 0; i < queuedInOrder.size(); i++) {
            if (queuedInOrder.get(i).getId().equals(item.getId())) {
                return i + 1;
            }
        }
        return null;
    }

    private List<String> currentRunPrUrls(SpecQueueItem item) {
        String runId = item.getWorkflowRunId();
        if (runId == null) {
            return List.of();
        }
        return pullRequests.findByItemIdOrderByCreatedAtAsc(item.getId()).stream()
                .filter(pr -> runId.equals(pr.getWorkflowRunId()))
                .map(SpecQueueItemPullRequest::getUrl)
                .toList();
    }
}
