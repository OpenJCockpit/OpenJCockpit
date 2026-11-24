package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.client.AgentEventDto;
import nl.metafactory.aicontrol.client.AgentRunDto;
import nl.metafactory.aicontrol.client.AgentRunOutputDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionAuditEntryDto;
import nl.metafactory.aicontrol.client.ApprovalDecisionKind;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.model.EvidenceRun;
import nl.metafactory.aicontrol.model.EvidenceSource;
import nl.metafactory.aicontrol.model.EvidenceStatus;
import nl.metafactory.aicontrol.model.EvidenceTimelineEvent;
import nl.metafactory.aicontrol.model.GitWorkspaceJob;
import nl.metafactory.aicontrol.model.GitWorkspaceJobEvent;
import nl.metafactory.aicontrol.model.GitWorkspaceJobStatus;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobEventRepository;
import nl.metafactory.aicontrol.repository.GitWorkspaceJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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

class EvidenceServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private ProjectService projectService;
    private GitWorkspaceJobRepository jobRepo;
    private GitWorkspaceJobEventRepository eventRepo;
    private EmbabelAgentClient embabel;
    private EvidenceService service;

    private UUID projectId;
    private GitWorkspaceJob job;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        jobRepo = mock(GitWorkspaceJobRepository.class);
        eventRepo = mock(GitWorkspaceJobEventRepository.class);
        embabel = mock(EmbabelAgentClient.class);
        service = new EvidenceService(projectService, jobRepo, eventRepo, embabel);

        projectId = UUID.randomUUID();
        job = job("run-1", T0);
        when(jobRepo.findFirstByProjectIdAndRunIdOrderByCreatedAtDesc(projectId, "run-1")).thenReturn(Optional.of(job));
        when(eventRepo.findAllByJobIdOrderByCreatedAtAsc(job.getId())).thenReturn(List.of());
        when(embabel.listApprovalDecisions("run-1")).thenReturn(List.of());
    }

    private GitWorkspaceJob job(String runId, Instant created) {
        var j = new GitWorkspaceJob();
        ReflectionTestUtils.setField(j, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(j, "createdAt", created);
        j.setProjectId(projectId);
        j.setRunId(runId);
        j.setStatus(GitWorkspaceJobStatus.AGENTS_RUNNING);
        j.setWorkflowId("wf-1");
        j.setStartedBy("alice");
        j.setWorkingBranch("feature/x");
        return j;
    }

    private AgentRunDto run(String status, List<AgentEventDto> events) {
        return new AgentRunDto("run-1", "c", "spec.md", "repo", status, T0, events, List.of("a.md"),
                "wf-1", "alice", null, null);
    }

    @Test
    void mergesAgentApprovalAndGitEventsByInstant() {
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.of(run("RUNNING", List.of(
                new AgentEventDto(T0.plusSeconds(20), "requirement", "Spec loaded", "OK", null),
                new AgentEventDto(T0.plusSeconds(60), "review", "Review", "WAITING", null)))));
        when(embabel.listApprovalDecisions("run-1")).thenReturn(List.of(new ApprovalDecisionAuditEntryDto(
                "d1", "run-1", "wf-1", "review", 1, ApprovalDecisionKind.DENY, "not good", "bob", "sub",
                null, null, null, T0.plusSeconds(40))));
        var gitEvent = new GitWorkspaceJobEvent(job.getId(), "BRANCH_CREATED", "Branch created: feature/x");
        ReflectionTestUtils.setField(gitEvent, "createdAt", T0.plusSeconds(10));
        when(eventRepo.findAllByJobIdOrderByCreatedAtAsc(job.getId())).thenReturn(List.of(gitEvent));

        EvidenceRun result = service.getRun(projectId, "run-1");

        assertThat(result.events()).extracting(EvidenceTimelineEvent::source)
                .containsExactly(EvidenceSource.GIT, EvidenceSource.AGENT, EvidenceSource.APPROVAL, EvidenceSource.AGENT);
        assertThat(result.events().get(0).title()).isEqualTo("Branch created");
        assertThat(result.events().get(2).status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(result.events().get(2).actor()).isEqualTo("bob");
        assertThat(result.events().get(2).detail()).isEqualTo("not good");
        assertThat(result.details().status()).isEqualTo("RUNNING");
        assertThat(result.details().artifacts()).containsExactly("a.md");
        assertThat(result.details().branch()).isEqualTo("feature/x");
    }

    @Test
    void shortensLongAgentTitleAndKeepsOutputRefAsAgentKey() {
        String longText = "First line of output\n" + "x".repeat(500);
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.of(run("OK", List.of(
                new AgentEventDto(T0, "impl", longText, "OK", "agent-output://impl")))));

        EvidenceTimelineEvent e = service.getRun(projectId, "run-1").events().get(0);

        assertThat(e.title()).isEqualTo("First line of output");
        assertThat(e.detail()).isEqualTo(longText.strip());
        assertThat(e.outputRef()).isEqualTo("impl");
    }

    @Test
    void shortTitleIsCappedAndShortTextHasNoDetail() {
        assertThat(EvidenceService.shortTitle("y".repeat(300))).hasSize(EvidenceService.MAX_TITLE_CHARS).endsWith("…");
        assertThat(EvidenceService.detailOf("Spec loaded")).isNull();
    }

    @Test
    void embabelDownFallsBackToJobDataAndGitEvents() {
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.empty());
        when(embabel.listApprovalDecisions("run-1")).thenThrow(new RuntimeException("down"));
        var gitEvent = new GitWorkspaceJobEvent(job.getId(), "FAILED", "CLONE_FAILED: x");
        when(eventRepo.findAllByJobIdOrderByCreatedAtAsc(job.getId())).thenReturn(List.of(gitEvent));

        EvidenceRun result = service.getRun(projectId, "run-1");

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(result.details().status()).isEqualTo("AGENTS_RUNNING");
        assertThat(result.details().workflowId()).isEqualTo("wf-1");
        assertThat(result.details().artifacts()).isEmpty();
    }

    @Test
    void runOfAnotherProjectIsNotFound() {
        assertThatThrownBy(() -> service.getRun(projectId, "other-run"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getOutput(projectId, "other-run", "impl"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void projectWithoutRunGetsEmptyListAndNotFoundLatest() {
        when(jobRepo.findAllByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(eq(projectId), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(jobRepo.findFirstByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(projectId)).thenReturn(Optional.empty());

        assertThat(service.listRuns(projectId, 20, 0).items()).isEmpty();
        assertThatThrownBy(() -> service.getLatestRun(projectId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listRunsPagesWithOffsetAndCountsEvents() {
        var second = job("run-2", T0.plusSeconds(100));
        when(jobRepo.findAllByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(eq(projectId), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(1);
                    assertThat(p.getOffset()).isEqualTo(5);
                    assertThat(p.getPageSize()).isEqualTo(2);
                    return new PageImpl<>(List.of(second, job), p, 7);
                });
        when(embabel.getLatestRun("run-2")).thenReturn(Optional.of(run("FAILED", List.of(
                new AgentEventDto(T0, "a", "t", "OK", null), new AgentEventDto(T0, "b", "t", "FAILED", null)))));
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.empty());

        var page = service.listRuns(projectId, 2, 5);

        assertThat(page.total()).isEqualTo(7);
        assertThat(page.offset()).isEqualTo(5);
        assertThat(page.items()).extracting(s -> s.runId()).containsExactly("run-2", "run-1");
        assertThat(page.items().get(0).eventCount()).isEqualTo(2);
        assertThat(page.items().get(0).failedEventCount()).isEqualTo(1);
        assertThat(page.items().get(1).eventCount()).isNull();
        assertThat(page.items().get(1).status()).isEqualTo("AGENTS_RUNNING");
    }

    @Test
    void outputIsResolvedByAgentKey() {
        when(embabel.getAgentRunOutputs("run-1")).thenReturn(List.of(new AgentRunOutputDto("impl", 1, "full text")));

        assertThat(service.getOutput(projectId, "run-1", "impl").output()).isEqualTo("full text");
        assertThatThrownBy(() -> service.getOutput(projectId, "run-1", "missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void outputWhenEmbabelDownIsBadGateway() {
        when(embabel.getAgentRunOutputs("run-1")).thenThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> service.getOutput(projectId, "run-1", "impl"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void latestRunIsTheNewestJobWithARun() {
        when(jobRepo.findFirstByProjectIdAndRunIdIsNotNullOrderByCreatedAtDesc(projectId)).thenReturn(Optional.of(job));
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.empty());

        assertThat(service.getLatestRun(projectId).details().runId()).isEqualTo("run-1");
    }

    @Test
    void approvalVerdictsMapToTitleAndStatus() {
        when(embabel.getLatestRun("run-1")).thenReturn(Optional.empty());
        when(embabel.listApprovalDecisions("run-1")).thenReturn(List.of(
                approval(ApprovalDecisionKind.ACCEPT, 1),
                approval(ApprovalDecisionKind.ACCEPT_WITH_COMMENTS, 2),
                approval(null, 3)));

        var events = service.getRun(projectId, "run-1").events();

        assertThat(events).extracting(EvidenceTimelineEvent::title).containsExactly(
                "Approval accepted: review", "Approval accepted with comments: review", "Approval decided: review");
        assertThat(events).extracting(EvidenceTimelineEvent::status).containsOnly(EvidenceStatus.OK);
    }

    @Test
    void agentStatusesMap() {
        assertThat(EvidenceService.toStatus("RUNNING")).isEqualTo(EvidenceStatus.RUNNING);
        assertThat(EvidenceService.toStatus("ok")).isEqualTo(EvidenceStatus.OK);
        assertThat(EvidenceService.toStatus("whatever")).isEqualTo(EvidenceStatus.WAITING);
        assertThat(EvidenceService.toStatus(null)).isEqualTo(EvidenceStatus.WAITING);
    }

    private ApprovalDecisionAuditEntryDto approval(ApprovalDecisionKind kind, int seconds) {
        return new ApprovalDecisionAuditEntryDto("d", "run-1", "wf-1", "review", 1, kind, null, "bob", "sub",
                null, null, null, T0.plusSeconds(seconds));
    }
}
