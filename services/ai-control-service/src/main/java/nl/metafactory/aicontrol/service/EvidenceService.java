package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentEventDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.EvidenceOutput;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceRunDetails;
import nl.metafactory.aicontrol.model.EvidenceRunPage;
import nl.metafactory.aicontrol.model.EvidenceRunSummary;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Serves the evidence of a project's workflow runs: the run list and, per run, one timeline that
 * merges the agent events, the human approval decisions and the git job events.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    static final String OUTPUT_REF_PREFIX = "agent-output://";
    static final int MAX_TITLE_CHARS = 120;

    private final ProjectService projectService;
    private final GitWorkspaceJobRepository jobRepo;
    private final GitWorkspaceJobEventRepository eventRepo;
    private final EmbabelAgentClient embabelAgentClient;

    public EvidenceService(ProjectService projectService,
                           GitWorkspaceJobRepository jobRepo,
                           GitWorkspaceJobEventRepository eventRepo,
                           EmbabelAgentClient embabelAgentClient) {
        this.projectService = projectService;
        this.jobRepo = jobRepo;
        this.eventRepo = eventRepo;
        this.embabelAgentClient = embabelAgentClient;
    }

    @Transactional(readOnly = true)
    public EvidenceRunPage listRuns(UUID projectId, int limit, int offset) {
        projectService.getOrThrow(projectId);
        Pageable pageable = new OffsetPageRequest(offset, limit);
        Page<GitWorkspaceJob> page = jobRepo.findAllByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(projectId, pageable);
        List<EvidenceRunSummary> items = page.getContent().stream().map(this::toSummary).toList();
        return new EvidenceRunPage(items, page.getTotalElements(), limit, offset);
    }

    /**
     * Run ids of a project (or of one workflow when {@code workflowId} is set) created in the window,
     * oldest first and without duplicates. Returns at most {@code limit} ids.
     */
    @Transactional(readOnly = true)
    public List<String> listRunIds(UUID projectId, String workflowId, Instant from, Instant to, int limit) {
        projectService.getOrThrow(projectId);
        Pageable pageable = new OffsetPageRequest(0, limit);
        List<String> ids = workflowId == null
                ? jobRepo.findRunIds(projectId, from, to, pageable)
                : jobRepo.findRunIdsByWorkflow(projectId, workflowId, from, to, pageable);
        return ids.stream().distinct().toList();
    }

    @Transactional(readOnly = true)
    public EvidenceRun getRun(UUID projectId, String runId) {
        projectService.getOrThrow(projectId);
        GitWorkspaceJob job = jobRepo.findFirstByProjectIdAndRunIdOrderByCreatedAtDesc(projectId, runId)
                .orElseThrow(() -> runNotFound(runId));
        return buildRun(job);
    }

    @Transactional(readOnly = true)
    public EvidenceRun getLatestRun(UUID projectId) {
        projectService.getOrThrow(projectId);
        GitWorkspaceJob job = jobRepo.findFirstByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project has no run yet: " + projectId));
        return buildRun(job);
    }

    /** Full output of one agent; loaded on demand because the timeline only carries a shortened title. */
    @Transactional(readOnly = true)
    public EvidenceOutput getOutput(UUID projectId, String runId, String agentKey) {
        projectService.getOrThrow(projectId);
        jobRepo.findFirstByProjectIdAndRunIdOrderByCreatedAtDesc(projectId, runId)
                .orElseThrow(() -> runNotFound(runId));
        List<nl.metafactory.aicontrol.client.AgentRunOutputDto> outputs;
        try {
            outputs = embabelAgentClient.getAgentRunOutputs(runId);
        } catch (Exception e) {
            log.warn("Could not load outputs of run {}: {}", runId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent service unavailable");
        }
        return outputs.stream()
                .filter(o -> agentKey.equals(o.agentId()))
                .findFirst()
                .map(o -> new EvidenceOutput(agentKey, o.output() != null ? o.output() : ""))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No output of agent " + agentKey + " in run " + runId));
    }

    // ── Run list ─────────────────────────────────────────────────────────────

    private EvidenceRunSummary toSummary(GitWorkspaceJob job) {
        AgentRunDto run = embabelAgentClient.getLatestRun(job.getRunId()).orElse(null);
        Integer total = null;
        Integer failed = null;
        if (run != null) {
            List<AgentEventDto> events = run.events() != null ? run.events() : List.of();
            total = events.size();
            failed = (int) events.stream().filter(e -> toStatus(e.status()) == EvidenceStatus.FAILED).count();
        }
        EvidenceRunDetails d = details(job, run);
        return new EvidenceRunSummary(d.runId(), d.workflowId(), d.status(), d.startedAt(), d.completedAt(),
                d.startedBy(), d.branch(), total, failed);
    }

    // ── Run evidence ─────────────────────────────────────────────────────────

    private EvidenceRun buildRun(GitWorkspaceJob job) {
        String runId = job.getRunId();
        AgentRunDto run = embabelAgentClient.getLatestRun(runId).orElse(null);
        Instant fallback = startInstant(job);

        List<EvidenceTimelineEvent> events = new java.util.ArrayList<>();
        if (run != null && run.events() != null) {
            run.events().forEach(e -> events.add(agentEvent(e, fallback)));
        }
        loadApprovals(runId).forEach(a -> events.add(approvalEvent(a, fallback)));
        eventRepo.findAllByJobIdOrderByCreatedAtAsc(job.getId()).forEach(e -> events.add(gitEvent(e)));
        events.sort(Comparator.comparing(EvidenceTimelineEvent::timestamp));

        return new EvidenceRun(details(job, run), events);
    }

    private List<ApprovalDecisionAuditEntryDto> loadApprovals(String runId) {
        try {
            return embabelAgentClient.listApprovalDecisions(runId);
        } catch (Exception e) {
            log.warn("Could not load approval decisions of run {}: {}", runId, e.getMessage());
            return List.of();
        }
    }

    private EvidenceRunDetails details(GitWorkspaceJob job, AgentRunDto run) {
        Instant startedAt = run != null && run.startedAt() != null ? run.startedAt() : startInstant(job);
        Instant completedAt = run != null && run.completedAt() != null ? run.completedAt() : job.getCompletedAt();
        return new EvidenceRunDetails(
                job.getRunId(),
                firstNonBlank(run != null ? run.workflowId() : null, job.getWorkflowId()),
                firstNonBlank(run != null ? run.status() : null, job.getStatus().name()),
                firstNonBlank(run != null ? run.startedBy() : null, job.getStartedBy()),
                startedAt,
                completedAt,
                job.getWorkingBranch(),
                job.getCommitHash(),
                run != null && run.generatedArtifacts() != null ? run.generatedArtifacts() : List.of(),
                firstNonBlank(run != null ? run.failureSummary() : null, job.getErrorMessage()));
    }

    private EvidenceTimelineEvent agentEvent(AgentEventDto e, Instant fallback) {
        String ref = e.evidenceRef();
        String outputRef = ref != null && ref.startsWith(OUTPUT_REF_PREFIX)
                ? ref.substring(OUTPUT_REF_PREFIX.length()) : null;
        String text = e.title() != null ? e.title() : "";
        return new EvidenceTimelineEvent(
                e.timestamp() != null ? e.timestamp() : fallback,
                EvidenceSource.AGENT, e.agentId(), null,
                shortTitle(text), detailOf(text), outputRef, toStatus(e.status()));
    }

    private EvidenceTimelineEvent approvalEvent(ApprovalDecisionAuditEntryDto a, Instant fallback) {
        String stage = a.gateStage() != null ? a.gateStage() : "approval gate";
        String verdict;
        EvidenceStatus status = EvidenceStatus.OK;
        if (a.decision() == null) {
            verdict = "decided";
        } else {
            switch (a.decision()) {
                case ACCEPT -> verdict = "accepted";
                case ACCEPT_WITH_COMMENTS -> verdict = "accepted with comments";
                default -> {
                    verdict = "denied";
                    status = EvidenceStatus.FAILED;
                }
            }
        }
        return new EvidenceTimelineEvent(
                a.timestamp() != null ? a.timestamp() : fallback,
                EvidenceSource.APPROVAL, null, a.actorUsername(),
                "Approval " + verdict + ": " + stage, blankToNull(a.comment()), null, status);
    }

    private EvidenceTimelineEvent gitEvent(GitWorkspaceJobEvent e) {
        EvidenceStatus status = "FAILED".equals(e.getEventType()) ? EvidenceStatus.FAILED : EvidenceStatus.OK;
        return new EvidenceTimelineEvent(e.getCreatedAt(), EvidenceSource.GIT, null, null,
                humanize(e.getEventType()), blankToNull(e.getMessage()), null, status);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static EvidenceStatus toStatus(String status) {
        if (status == null) return EvidenceStatus.WAITING;
        return switch (status.toUpperCase()) {
            case "OK" -> EvidenceStatus.OK;
            case "RUNNING" -> EvidenceStatus.RUNNING;
            case "FAILED" -> EvidenceStatus.FAILED;
            default -> EvidenceStatus.WAITING;
        };
    }

    /** First line of the text, capped to {@link #MAX_TITLE_CHARS}. */
    static String shortTitle(String text) {
        String firstLine = text.strip().lines().findFirst().orElse("").strip();
        return firstLine.length() <= MAX_TITLE_CHARS
                ? firstLine : firstLine.substring(0, MAX_TITLE_CHARS - 1).stripTrailing() + "…";
    }

    /** The full text when the title had to cut something off, otherwise null. */
    static String detailOf(String text) {
        String stripped = text.strip();
        return stripped.equals(shortTitle(stripped)) ? null : stripped;
    }

    /** {@code BRANCH_CREATED} becomes {@code Branch created}. */
    static String humanize(String eventType) {
        if (eventType == null || eventType.isBlank()) return "Event";
        String lower = eventType.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Instant startInstant(GitWorkspaceJob job) {
        return job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : blankToNull(b);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static ResponseStatusException runNotFound(String runId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: " + runId);
    }

    /** Offset based paging: {@link PageRequest} only supports page-aligned offsets. */
    private static final class OffsetPageRequest extends PageRequest {
        private final long offset;

        OffsetPageRequest(long offset, int size) {
            super(0, size, org.springframework.data.domain.Sort.unsorted());
            this.offset = offset;
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }
}
