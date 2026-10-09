package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.client.AgentEventDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueState;
import nl.metafactory.aicontrol.service.GitHubPullRequestState;
import nl.metafactory.aicontrol.specqueue.github.*;
import nl.metafactory.aicontrol.specqueue.planner.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PureUnitsTest {

    private static final String GIT = "https://github.com/Acme/Repo.git";
    private final RunOutcomeClassifier classifier = new RunOutcomeClassifier();

    // ---- URL parser
    @Test
    void parsesValidUrlAndCanonicalises() {
        var r = PullRequestUrlParser.parse("https://GitHub.com/acme/repo/pull/12", "https://user@github.com/acme/repo/");
        assertThat(r).isInstanceOf(PullRequestUrlParser.Valid.class);
        var v = (PullRequestUrlParser.Valid) r;
        assertThat(v.canonicalUrl()).isEqualTo("https://github.com/acme/repo/pull/12");
        assertThat(v.ref().number()).isEqualTo(12);
    }

    @Test
    void rejectsBadPrUrls() {
        for (String u : List.of("http://github.com/acme/repo/pull/1", "https://github.com/acme/repo/pull/0",
                "https://github.com/acme/repo/pull/01", "https://github.com/acme/repo/pull/2147483648",
                "https://u@github.com/acme/repo/pull/1", "https://github.com:444/acme/repo/pull/1",
                "https://evil.com/acme/repo/pull/1", "https://github.com/acme/other/pull/1",
                "https://github.com/../repo/pull/1", "https://github.com/acme/repo/pull/1?x=1",
                "https://github.com/acme/repo/pull/1#f", "https://github.com/acme/repo/pull/1/files",
                "https://github.com/acme/repo/pull/1 ", "https://github.com/acme/%72epo/pull/1")) {
            assertThat(PullRequestUrlParser.parse(u, GIT)).as(u).isEqualTo(
                    new PullRequestUrlParser.Invalid(SpecQueueFailureReason.PR_URL_INVALID));
        }
        assertThat(PullRequestUrlParser.parse("https://github.com/acme/repo/pull/2147483647", GIT))
                .isInstanceOf(PullRequestUrlParser.Valid.class);
    }

    @Test
    void sshProjectUrlUnsupportedButMalformedPrReportedFirst() {
        assertThat(PullRequestUrlParser.parse("https://github.com/acme/repo/pull/1", "git@github.com:acme/repo.git"))
                .isEqualTo(new PullRequestUrlParser.Invalid(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));
        assertThat(PullRequestUrlParser.parse("nope", "git@github.com:acme/repo.git"))
                .isEqualTo(new PullRequestUrlParser.Invalid(SpecQueueFailureReason.PR_URL_INVALID));
    }

    // ---- API base
    @Test
    void apiBase() {
        assertThat(GitHubApiBaseResolver.resolve("GitHub.com", null)).contains("https://api.github.com");
        assertThat(GitHubApiBaseResolver.resolve("ghe.corp:8443", "")).contains("https://ghe.corp:8443/api/v3");
        assertThat(GitHubApiBaseResolver.resolve("github.com", "https://x.corp/api/v3/")).contains("https://x.corp/api/v3");
        assertThat(GitHubApiBaseResolver.resolve("github.com", "https://u@x.corp/api")).isEmpty();
        assertThat(GitHubApiBaseResolver.resolve("github.com", "https://x.corp/a/../b")).isEmpty();
        assertThat(GitHubApiBaseResolver.resolve("github.com", "https://x.corp/a?q=1")).isEmpty();
        assertThat(GitHubApiBaseResolver.resolve("github.com", "ftp://x.corp")).isEmpty();
        assertThat(GitHubApiBaseResolver.resolve("bad host", null)).isEmpty();
    }

    // ---- Mergeability
    private static GitHubPullRequestState st(boolean open, boolean merged, boolean draft, Boolean mergeable,
                                             String ms, int pending, int failing) {
        return new GitHubPullRequestState(open, merged, draft, mergeable, ms, "sha", "main", pending, failing, 0);
    }

    @Test
    void mergeability() {
        assertThat(MergeabilityEvaluator.evaluate(st(false, true, true, true, "clean", 0, 0))).isEqualTo(MergeDecision.ALREADY_MERGED);
        assertThat(MergeabilityEvaluator.evaluate(st(false, false, true, true, "clean", 0, 0))).isEqualTo(MergeDecision.CLOSED_UNMERGED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, true, true, "clean", 0, 0))).isEqualTo(MergeDecision.BLOCKED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, null, "clean", 0, 0))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, null, 0, 0))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "UNKNOWN", 0, 0))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, false, "dirty", 0, 0))).isEqualTo(MergeDecision.CONFLICT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "CLEAN", 0, 0))).isEqualTo(MergeDecision.MERGE);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "has_hooks", 0, 0))).isEqualTo(MergeDecision.MERGE);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "blocked", 1, 0))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "blocked", 0, 1))).isEqualTo(MergeDecision.BLOCKED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "blocked", 0, 0))).isEqualTo(MergeDecision.BLOCKED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "unstable", 1, 1))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "unstable", 0, 1))).isEqualTo(MergeDecision.BLOCKED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "unstable", 0, 0))).isEqualTo(MergeDecision.WAIT);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "behind", 0, 0))).isEqualTo(MergeDecision.BLOCKED);
        assertThat(MergeabilityEvaluator.evaluate(st(true, false, false, true, "weird", 0, 0))).isEqualTo(MergeDecision.BLOCKED);
        assertThatNullPointerException().isThrownBy(() -> MergeabilityEvaluator.evaluate(null));
    }

    // ---- Classifier
    private static AgentRunDto run(String status, List<AgentEventDto> events, List<String> artifacts) {
        return new AgentRunDto("r", null, null, null, status, Instant.parse("2026-01-01T00:00:00Z"),
                events, artifacts, null, null, Instant.parse("2026-01-01T00:00:10Z"), "secret prose");
    }

    @Test
    void classifiesTerminalStatuses() {
        assertThat(classifier.classify(run(" failed ", null, null), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.RUN_FAILED));
        assertThat(classifier.classify(run("DENIED", null, null), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.APPROVAL_DENIED));
        assertThat(classifier.classify(run("CANCELLED", null, null), true, GIT)).isInstanceOf(RunOutcome.CancelledByQueue.class);
        assertThat(classifier.classify(run("CANCELLED", null, null), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.RUN_CANCELLED));
        assertThat(classifier.classify(run("weird", null, null), false, GIT)).isEqualTo(new RunOutcome.NonTerminal("OTHER"));
        assertThat(classifier.classify(run(null, null, null), false, GIT)).isEqualTo(new RunOutcome.NonTerminal("OTHER"));
    }

    @Test
    void classifiesCompleted() {
        var failedEvent = List.of(new AgentEventDto(null, "pub", "t", "FAILED", null));
        assertThat(classifier.classify(run("COMPLETED", failedEvent, List.of("pull-request:junk")), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.PUBLICATION_FAILED));
        assertThat(classifier.classify(run("COMPLETED", null, null), false, GIT)).isInstanceOf(RunOutcome.SucceededNoChanges.class);
        assertThat(classifier.classify(run("COMPLETED", null, List.of("git-branch:x")), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.PR_NOT_CREATED));
        var one = classifier.classify(run("COMPLETED", null, Arrays.asList(null, "pull-request: https://github.com/acme/repo/pull/3",
                "pull-request:https://GITHUB.com/acme/repo/pull/3")), false, GIT);
        assertThat(one).isInstanceOf(RunOutcome.SucceededWithPr.class);
        var two = classifier.classify(run("COMPLETED", null, List.of("pull-request:https://github.com/acme/repo/pull/3",
                "pull-request:https://github.com/acme/repo/pull/4")), false, GIT);
        assertThat(two.pullRequests()).hasSize(2);
        assertThat(classifier.classify(run("COMPLETED", null, List.of("pull-request:https://github.com/acme/repo/pull/3",
                "pull-request:https://github.com/other/repo/pull/4")), false, GIT))
                .isEqualTo(new RunOutcome.Failed(SpecQueueFailureReason.PR_URL_INVALID));
    }

    @Test
    void features() {
        var events = List.of(new AgentEventDto(null, "review", "t", "OK", null),
                new AgentEventDto(null, "review", "t", "WAITING", null),
                new AgentEventDto(null, "x", "t", "AWAITING_APPROVAL", null));
        var f = classifier.features(run("DENIED", events, null));
        assertThat(f.durationMs()).isEqualTo(10_000);
        assertThat(f.reviewIterations()).isEqualTo(2);
        assertThat(f.approvalGateIterations()).isEqualTo(1);
        assertThat(f.approvalGateOutcome()).isEqualTo("DENIED");
        assertThat(classifier.features(run("COMPLETED", null, null)).approvalGateOutcome()).isEqualTo("NONE");
        var neg = new AgentRunDto("r", null, null, null, "COMPLETED", Instant.parse("2026-01-01T00:00:10Z"), null, null,
                null, null, Instant.parse("2026-01-01T00:00:00Z"), null);
        assertThat(classifier.features(neg).durationMs()).isZero();
        assertThat(classifier.features(new AgentRunDto("r", null, null, null, null, null, null, null, null, null, null, null)).durationMs()).isNull();
    }

    // ---- Planner / guardrails
    private static PlannerItemView item(int pos, boolean eff) {
        return new PlannerItemView(UUID.randomUUID(), "s", "w", pos, eff, eff);
    }

    private static final PlannerProjectView PV = new PlannerProjectView(UUID.randomUUID(), SpecQueueState.ACTIVE, true);
    private static final RunOutcome.SucceededWithPr ONE = new RunOutcome.SucceededWithPr(new RunPullRequest("u", "o", "r", 1));

    @Test
    void fifoPicksLowestPositionAndDecides() {
        var fifo = new FifoQueuePlanner();
        var a = item(5, false);
        var b = item(2, false);
        assertThat(fifo.selectNext(PV, List.of(a, b), PlannerHistory.empty())).contains(b.id());
        assertThat(fifo.selectNext(PV, List.of(), PlannerHistory.empty())).isEmpty();
        assertThat(fifo.onRunFinished(a, new RunOutcome.Failed(SpecQueueFailureReason.RUN_FAILED))).isEqualTo(RunFinishedDecision.HALT);
        assertThat(fifo.onRunFinished(a, new RunOutcome.SucceededNoChanges())).isEqualTo(RunFinishedDecision.CONTINUE);
        assertThat(fifo.onRunFinished(a, ONE)).isEqualTo(RunFinishedDecision.WAIT_FOR_MERGE);
        assertThat(fifo.onRunFinished(item(1, true), ONE)).isEqualTo(RunFinishedDecision.MERGE_AND_CONTINUE);
    }

    private static QueuePlanner planner(RunFinishedDecision d, Optional<UUID> sel) {
        return new QueuePlanner() {
            public String version() { return "T"; }
            public Optional<UUID> selectNext(PlannerProjectView p, List<PlannerItemView> q, PlannerHistory h) { return sel; }
            public RunFinishedDecision onRunFinished(PlannerItemView i, RunOutcome o) { return d; }
        };
    }

    @Test
    void guardrailsSelection() {
        var a = item(1, false);
        assertThat(PlannerGuardrails.selectNext(planner(null, Optional.of(a.id())), PV, List.of(a), PlannerHistory.empty()).itemId()).contains(a.id());
        assertThat(PlannerGuardrails.selectNext(planner(null, Optional.empty()), PV, List.of(a), PlannerHistory.empty()).override()).isEmpty();
        assertThat(PlannerGuardrails.selectNext(planner(null, Optional.of(UUID.randomUUID())), PV, List.of(a), PlannerHistory.empty()).override())
                .contains(PlannerOverride.INVALID_SELECTION);
        assertThat(PlannerGuardrails.selectNext(planner(null, null), PV, List.of(a), PlannerHistory.empty()).override())
                .contains(PlannerOverride.INVALID_SELECTION);
        QueuePlanner thrower = new FifoQueuePlanner() {
            @Override public Optional<UUID> selectNext(PlannerProjectView p, List<PlannerItemView> q, PlannerHistory h) { throw new IllegalStateException(); }
        };
        assertThat(PlannerGuardrails.selectNext(thrower, PV, List.of(a), PlannerHistory.empty()).override()).contains(PlannerOverride.PLANNER_FAILED);
    }

    @Test
    void guardrailsDecisions() {
        var plain = item(1, false);
        var eff = item(1, true);
        var failed = new RunOutcome.Failed(SpecQueueFailureReason.RUN_FAILED);
        var none = new RunOutcome.SucceededNoChanges();
        var many = new RunOutcome.SucceededWithPrs(List.of(new RunPullRequest("a", "o", "r", 1), new RunPullRequest("b", "o", "r", 2)));
        assertThat(PlannerGuardrails.decideAfterRun(planner(null, null), plain, none).override()).contains(PlannerOverride.NULL_DECISION);
        assertThat(PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.CONTINUE, null), plain, new RunOutcome.NonTerminal("X")))
                .isEqualTo(new PlannerGuardrails.Decision(RunFinishedDecision.HALT, Optional.of(PlannerOverride.UNEXPECTED_OUTCOME)));
        var forced = PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.CONTINUE, null), plain, failed);
        assertThat(forced.decision()).isEqualTo(RunFinishedDecision.HALT);
        assertThat(forced.override()).contains(PlannerOverride.FAILURE_FORCES_HALT);
        var noPr = PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.MERGE_AND_CONTINUE, null), plain, none);
        assertThat(noPr.decision()).isEqualTo(RunFinishedDecision.CONTINUE);
        assertThat(noPr.override()).contains(PlannerOverride.NO_PULL_REQUESTS);
        var present = PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.CONTINUE, null), plain, ONE);
        assertThat(present.decision()).isEqualTo(RunFinishedDecision.WAIT_FOR_MERGE);
        assertThat(PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.MERGE_AND_CONTINUE, null), eff, ONE).decision())
                .isEqualTo(RunFinishedDecision.MERGE_AND_CONTINUE);
        var denied = PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.MERGE_AND_CONTINUE, null), plain, ONE);
        assertThat(denied.override()).contains(PlannerOverride.MERGE_NOT_PERMITTED);
        assertThat(PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.MERGE_AND_CONTINUE, null), eff, many).decision())
                .isEqualTo(RunFinishedDecision.WAIT_FOR_MERGE);
        assertThat(PlannerGuardrails.decideAfterRun(planner(RunFinishedDecision.HALT, null), plain, none).decision()).isEqualTo(RunFinishedDecision.HALT);
    }

    @Test
    void outcomeAndHistoryAreImmutable() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunOutcome.SucceededWithPrs(List.of(new RunPullRequest("a", "o", "r", 1))));
        var list = new ArrayList<>(List.of(new PlannerHistoryEntry(null, null)));
        var h = new PlannerHistory(list);
        list.clear();
        assertThat(h.entries()).hasSize(1);
        assertThatThrownBy(() -> h.entries().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
