package nl.metafactory.agents.approval;

import nl.metafactory.agents.approval.model.ApprovalDecisionAuditEntry;
import nl.metafactory.agents.approval.model.ApprovalDecisionCommand;
import nl.metafactory.agents.approval.model.ApprovalDecisionKind;
import nl.metafactory.agents.approval.model.ApprovalDecisionResult;
import nl.metafactory.agents.approval.model.ApprovalGateState;
import nl.metafactory.agents.approval.model.GateComment;
import nl.metafactory.agents.config.ApprovalGateProperties;
import nl.metafactory.agents.orchestration.AgentRunStore;
import nl.metafactory.agents.orchestration.PipelineContinuation;
import nl.metafactory.agents.orchestration.PipelineState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Validates and applies a decision against the currently open gate, and is the ONLY writer of the
 * {@code DENIED} status (BR-15/A6). ACCEPT and DENY are unconditional (subject to iteration/open
 * checks); {@code ACCEPT_WITH_COMMENTS} additionally requires {@code feedbackSupported} (BR-41,
 * computed once in {@link ApprovalGateCoordinator} and read-only here), the feedback iteration
 * bound (BR-27/AC-24), and a valid comment (BR-30/AC-29/AC-30) — a rejection on any of those
 * leaves the gate open at the same iteration, exactly as if nothing had happened (AC-59).
 *
 * <p>Single-owner invariant: the parked state is evicted from {@link ApprovalGateRegistry} and its
 * gate flipped closed BEFORE resubmitting to {@link PipelineContinuation#resume}, so two
 * simultaneous decisions against the same run can never both win (see
 * {@code ApprovalDecisionServiceConcurrencyTest}).</p>
 */
@Service
public class ApprovalDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDecisionService.class);

    private final ApprovalGateRegistry registry;
    private final AgentRunStore runStore;
    private final ApprovalDecisionAuditRepository auditRepository;
    private final ApprovalGateProperties properties;
    private final PipelineContinuation continuation;

    public ApprovalDecisionService(ApprovalGateRegistry registry, AgentRunStore runStore,
                                    ApprovalDecisionAuditRepository auditRepository,
                                    ApprovalGateProperties properties, PipelineContinuation continuation) {
        this.registry = registry;
        this.runStore = runStore;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.continuation = continuation;
    }

    public ApprovalDecisionResult submit(String runId, ApprovalDecisionCommand command) {
        // AC-33: unknown run id → 404, nothing created.
        if (runStore.get(runId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: " + runId);
        }

        // AC-59/AC-24/AC-29/AC-30: validated against a PEEK (not yet evicted) so a rejection here
        // touches nothing at all — the gate remains open at the same iteration exactly as if
        // nothing had happened. No LLM call, no re-run, no audit entry, no eviction. A genuine
        // concurrent race between two ACCEPT_WITH_COMMENTS submissions is still made safe by the
        // atomic evictIfPresent below: at most one of them can actually win the eviction.
        if (command.decision() == ApprovalDecisionKind.ACCEPT_WITH_COMMENTS) {
            validateAcceptWithComments(registry.get(runId), command);
        }

        // Single-owner invariant: validate-and-evict happens atomically under the registry's own
        // map operation, so two concurrent decisions against the same run can never both proceed.
        PipelineState state = registry.evictIfPresent(runId, candidate -> validateOrReject(candidate, command));

        if (state == null) {
            // Either genuinely not parked (AC-32: still running, mid-re-run, or already terminal),
            // or a second concurrent decision arrived after the first already evicted this run and
            // recorded its decision for that iteration (AC-31 409).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Run " + runId + " is not currently awaiting approval, or this decision was already recorded");
        }

        ApprovalGateState gate = state.gate();
        gate.markDecided(command.iteration());
        gate.setOpen(false);

        audit(state, command, gate);

        return switch (command.decision()) {
            case ACCEPT -> accept(state, gate, command);
            case DENY -> deny(state, gate, command);
            case ACCEPT_WITH_COMMENTS -> acceptWithComments(state, gate, command);
        };
    }

    /**
     * BR-41/AC-59 (placement), BR-27/AC-24 (bound), BR-30/AC-29/AC-30 (comment) — all three must
     * hold before any state is touched. {@code feedbackSupported} is read from the gate, never
     * re-derived from the placement stage name (risk 8): this is the ONE other place in the
     * backend that reads it, and it never computes it.
     */
    private void validateAcceptWithComments(PipelineState peeked, ApprovalDecisionCommand command) {
        if (peeked == null || peeked.gate() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is not currently awaiting approval");
        }
        ApprovalGateState gate = peeked.gate();
        if (!gate.feedbackSupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Feedback is not supported for placement stage '" + gate.placementStage() + "'");
        }
        if (gate.iteration() > properties.getMaxFeedbackIterations()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The feedback iteration limit (" + properties.getMaxFeedbackIterations()
                            + ") for this run has been reached");
        }
        String comment = command.comment();
        if (comment == null || comment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment must not be empty");
        }
        if (comment.length() > properties.getCommentMaxLength()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comment exceeds the maximum length of " + properties.getCommentMaxLength() + " characters");
        }
    }

    /**
     * Runs entirely under the registry's atomic {@code compute} — returning {@code null} here
     * means the decision is rejected and the entry is retained/kept parked exactly as it was
     * (AC-31/AC-32: the gate remains open at the same iteration when a decision is refused), NOT
     * evicted; only a non-null return causes {@link ApprovalGateRegistry#evictIfPresent} to
     * actually remove the entry.
     */
    private PipelineState validateOrReject(PipelineState candidate, ApprovalDecisionCommand command) {
        ApprovalGateState gate = candidate.gate();
        if (gate == null || !gate.isOpen()) {
            return null;
        }
        if (command.iteration() != gate.iteration() || gate.decidedIterations().contains(command.iteration())) {
            return null;
        }
        return candidate;
    }

    private ApprovalDecisionResult accept(PipelineState state, ApprovalGateState gate, ApprovalDecisionCommand command) {
        gate.setAcceptedFinal(true);
        runStore.setStatus(state.runId(), "RUNNING");
        log.info("approval.gate.resumed runId={} iteration={}", state.runId(), command.iteration());
        continuation.resume(state);
        return new ApprovalDecisionResult(state.runId(), "RUNNING", command.iteration(), "Accepted");
    }

    private ApprovalDecisionResult deny(PipelineState state, ApprovalGateState gate, ApprovalDecisionCommand command) {
        runStore.setStatus(state.runId(), "DENIED");
        log.info("approval.gate.denied runId={} iteration={}", state.runId(), command.iteration());
        return new ApprovalDecisionResult(state.runId(), "DENIED", command.iteration(), "Denied");
    }

    /** BR-17…BR-23: re-opens the realisation stage's block and resumes with the comment carried through. */
    private ApprovalDecisionResult acceptWithComments(PipelineState state, ApprovalGateState gate,
                                                        ApprovalDecisionCommand command) {
        gate.addComment(new GateComment(command.iteration(), command.actorUsername(), command.comment(), Instant.now()));
        gate.setPendingFeedback(command.comment());
        // Re-opens whichever stage this gate is placed on — by construction that is always the
        // feedback-supported stage here (feedbackSupported was validated true above), so this
        // never hardcodes the placement stage name and stays correct if the extension seam
        // (out-of-scope item 17) is ever widened to another stage.
        state.reopenStage(gate.placementStage());
        runStore.setStatus(state.runId(), "RUNNING");
        log.info("approval.feedback.iteration.started runId={} iteration={}", state.runId(), command.iteration());
        continuation.resume(state);
        return new ApprovalDecisionResult(state.runId(), "RUNNING", command.iteration(), "Accepted with comments");
    }

    private void audit(PipelineState state, ApprovalDecisionCommand command, ApprovalGateState gate) {
        var report = gate.lastReport();
        var entry = new ApprovalDecisionAuditEntry(
                UUID.randomUUID().toString(),
                state.runId(),
                workflowId(state),
                gate.placementStage(),
                command.iteration(),
                command.decision(),
                command.decision() == ApprovalDecisionKind.ACCEPT_WITH_COMMENTS ? command.comment() : null,
                command.actorUsername(),
                command.actorSubject(),
                report != null ? report.outcome() : null,
                gate.retainedBranch(),
                gate.retainedPullRequestUrl(),
                Instant.now());
        auditRepository.save(entry);
        log.info("approval.decision.received runId={} iteration={} decision={} actorUsername={}",
                state.runId(), command.iteration(), command.decision(), command.actorUsername());
    }

    // Deliberately re-derives the workflow id the same way SpecGitPublisher/ApprovalGateContextAssembler
    // do, rather than widening a forbidden file's visibility (work plan §4).
    private static final String WORKFLOW_PREFIX = "workflow:";

    private static String workflowId(PipelineState state) {
        String requestedBy = state.request().requestedBy();
        if (requestedBy != null && requestedBy.startsWith(WORKFLOW_PREFIX)
                && requestedBy.length() > WORKFLOW_PREFIX.length()) {
            return requestedBy.substring(WORKFLOW_PREFIX.length());
        }
        return null;
    }
}
