package nl.metafactory.agents.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaAgentRunPersistence implements AgentRunPersistencePort {

    private static final int MAX_WORKFLOW_ID_BATCH = 500;

    private final AgentRunRecordRepository agentRunRecordRepository;
    private final AgentRunEventRecordRepository agentRunEventRecordRepository;
    private final AgentRunArtifactRecordRepository agentRunArtifactRecordRepository;
    private final AgentRunHistoryQueryRepository agentRunHistoryQueryRepository;

    public JpaAgentRunPersistence(AgentRunRecordRepository agentRunRecordRepository,
                                  AgentRunEventRecordRepository agentRunEventRecordRepository,
                                  AgentRunArtifactRecordRepository agentRunArtifactRecordRepository,
                                  AgentRunHistoryQueryRepository agentRunHistoryQueryRepository) {
        this.agentRunRecordRepository = agentRunRecordRepository;
        this.agentRunEventRecordRepository = agentRunEventRecordRepository;
        this.agentRunArtifactRecordRepository = agentRunArtifactRecordRepository;
        this.agentRunHistoryQueryRepository = agentRunHistoryQueryRepository;
    }

    @Override
    @Transactional
    public void recordNew(String runId, String workflowId, String customerId, String specFile,
                          String repositoryUrl, String status, Instant startedAt, String startedBy) {
        String maskedUrl = RepositoryUrlSanitizer.mask(repositoryUrl);
        AgentRunRecord record = new AgentRunRecord(runId, workflowId, customerId, specFile,
                maskedUrl, status, startedAt, null, startedBy, null, null);
        agentRunRecordRepository.save(record);
    }

    @Override
    @Transactional
    public void appendEvent(String runId, int sequenceNo, Instant occurredAt, String agentId,
                            String title, String status, String evidenceRef) {
        AgentRunEventRecord record = new AgentRunEventRecord(runId, sequenceNo, occurredAt, agentId, title, status, evidenceRef);
        agentRunEventRecordRepository.saveAndFlush(record);
    }

    @Override
    @Transactional
    public void appendArtifact(String runId, int sequenceNo, String artifact) {
        AgentRunArtifactRecord record = new AgentRunArtifactRecord(runId, sequenceNo, artifact);
        agentRunArtifactRecordRepository.saveAndFlush(record);
    }

    @Override
    @Transactional
    public void flushTerminal(String runId, String finalStatus, Instant completedAt, String failureSummary,
                              List<EventSnapshot> events, List<String> artifacts) {
        AgentRunRecord record = agentRunRecordRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("AgentRunRecord not found for runId: " + runId));
        record.setStatus(finalStatus);
        record.setCompletedAt(completedAt);
        if (failureSummary != null) {
            record.setFailureSummary(failureSummary);
        }
        agentRunRecordRepository.save(record);

        Integer maxEventSeq = agentRunEventRecordRepository.findMaxSequenceNoByRunId(runId);
        int eventMax = maxEventSeq != null ? maxEventSeq : -1;
        if (events != null) {
            for (EventSnapshot snapshot : events) {
                if (snapshot.sequenceNo() > eventMax) {
                    AgentRunEventRecord eventRecord = new AgentRunEventRecord(
                            runId, snapshot.sequenceNo(), snapshot.occurredAt(),
                            snapshot.agentId(), snapshot.title(), snapshot.status(), snapshot.evidenceRef());
                    agentRunEventRecordRepository.save(eventRecord);
                }
            }
        }

        Integer maxArtifactSeq = agentRunArtifactRecordRepository.findMaxSequenceNoByRunId(runId);
        int artifactMax = maxArtifactSeq != null ? maxArtifactSeq : -1;
        if (artifacts != null) {
            for (int i = 0; i < artifacts.size(); i++) {
                if (i > artifactMax) {
                    AgentRunArtifactRecord artifactRecord = new AgentRunArtifactRecord(runId, i, artifacts.get(i));
                    agentRunArtifactRecordRepository.save(artifactRecord);
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PersistedRunSummaryPage listByWorkflow(String workflowId, int limit, int offset) {
        int safeLimit = Math.max(limit, 1);
        int safeOffset = Math.max(offset, 0);
        int fetchSize = safeOffset + safeLimit;

        List<AgentRunSummaryProjection> projections =
                agentRunHistoryQueryRepository.findSummariesByWorkflowId(workflowId, PageRequest.of(0, fetchSize));

        int fromIndex = Math.min(safeOffset, projections.size());
        int toIndex = Math.min(safeOffset + safeLimit, projections.size());
        List<PersistedRunSummary> items = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            AgentRunSummaryProjection p = projections.get(i);
            items.add(new PersistedRunSummary(p.getRunId(), p.getWorkflowId(), p.getStatus(),
                    p.getStartedAt(), p.getCompletedAt(), p.getStartedBy()));
        }

        long total = agentRunHistoryQueryRepository.countByWorkflowId(workflowId);
        boolean hasMore = (safeOffset + items.size()) < total;
        return new PersistedRunSummaryPage(items, total, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FullRun> findFullRun(String runId) {
        Optional<AgentRunRecord> recordOpt = agentRunRecordRepository.findById(runId);
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }
        AgentRunRecord record = recordOpt.get();

        List<EventSnapshot> events = new ArrayList<>();
        for (AgentRunEventRecord eventRecord : agentRunEventRecordRepository.findByRunIdOrderBySequenceNoAsc(runId)) {
            events.add(new EventSnapshot(eventRecord.getSequenceNo(), eventRecord.getOccurredAt(),
                    eventRecord.getAgentId(), eventRecord.getTitle(), eventRecord.getStatus(), eventRecord.getEvidenceRef()));
        }

        List<String> artifacts = new ArrayList<>();
        for (AgentRunArtifactRecord artifactRecord : agentRunArtifactRecordRepository.findByRunIdOrderBySequenceNoAsc(runId)) {
            artifacts.add(artifactRecord.getArtifact());
        }

        FullRun fullRun = new FullRun(record.getRunId(), record.getWorkflowId(), record.getCustomerId(),
                record.getSpecFile(), record.getRepositoryUrl(), record.getStatus(),
                record.getStartedAt(), record.getCompletedAt(), record.getStartedBy(), record.getReconciledAt(),
                events, artifacts, record.getFailureSummary());
        return Optional.of(fullRun);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, LastExecution> findLatestRunPerWorkflow(Collection<String> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return Map.of();
        }
        Map<String, LastExecution> result = new HashMap<>();
        List<String> idList = new ArrayList<>(workflowIds);
        for (int start = 0; start < idList.size(); start += MAX_WORKFLOW_ID_BATCH) {
            int end = Math.min(start + MAX_WORKFLOW_ID_BATCH, idList.size());
            List<String> chunk = idList.subList(start, end);
            List<WorkflowLastExecutionProjection> projections =
                    agentRunHistoryQueryRepository.findLatestRunPerWorkflow(chunk);
            for (WorkflowLastExecutionProjection projection : projections) {
                result.put(projection.getWorkflowId(),
                        new LastExecution(projection.getWorkflowId(), projection.getStatus(), projection.getStartedAt()));
            }
        }
        return result;
    }
}
