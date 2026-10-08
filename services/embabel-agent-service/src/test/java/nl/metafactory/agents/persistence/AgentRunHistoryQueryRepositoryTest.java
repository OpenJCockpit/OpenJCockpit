package nl.metafactory.agents.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.data.domain.PageRequest;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class AgentRunHistoryQueryRepositoryTest {

    @Autowired
    private AgentRunRecordRepository agentRunRecordRepository;

    @Autowired
    private AgentRunEventRecordRepository agentRunEventRecordRepository;

    @Autowired
    private AgentRunArtifactRecordRepository agentRunArtifactRecordRepository;

    @Autowired
    private AgentRunHistoryQueryRepository queryRepository;

    @Test
    void summaryProjectionHasNoEventOrArtifactFields() {
        Set<String> fieldNames = Arrays.stream(AgentRunSummaryProjection.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> expected = new HashSet<>(Arrays.asList(
                "runId", "workflowId", "status", "startedAt", "completedAt", "startedBy"));

        assertThat(fieldNames).containsExactlyInAnyOrderElementsOf(expected);

        Set<String> forbidden = fieldNames.stream()
                .filter(name -> name.toLowerCase().contains("event") || name.toLowerCase().contains("artifact"))
                .collect(Collectors.toSet());
        assertThat(forbidden).isEmpty();
    }

    @Test
    void summaryQueryProducesCorrectResultRegardlessOfChildRowCount() {
        Instant now = Instant.now();
        agentRunRecordRepository.save(new AgentRunRecord(
                "run-many-children", "wf-proj", null, null, null, "COMPLETED",
                now.minusSeconds(5), now, null, null, null));

        for (int i = 0; i < 50; i++) {
            agentRunEventRecordRepository.save(new AgentRunEventRecord(
                    "run-many-children", i, now, "agent", "t" + i, "DONE", null));
            agentRunArtifactRecordRepository.save(new AgentRunArtifactRecord(
                    "run-many-children", i, "artifact-" + i));
        }

        agentRunRecordRepository.save(new AgentRunRecord(
                "run-no-children", "wf-proj", null, null, null, "COMPLETED",
                now.minusSeconds(10), now, null, null, null));

        List<AgentRunSummaryProjection> summaries =
                queryRepository.findSummariesByWorkflowId("wf-proj", PageRequest.of(0, 10));

        assertThat(summaries).hasSize(2);
        assertThat(queryRepository.countByWorkflowId("wf-proj")).isEqualTo(2);

        Set<String> expectedRunIds = new HashSet<>(Arrays.asList("run-many-children", "run-no-children"));
        Set<String> actualRunIds = summaries.stream()
                .map(AgentRunSummaryProjection::getRunId)
                .collect(Collectors.toSet());
        assertThat(actualRunIds).containsExactlyInAnyOrderElementsOf(expectedRunIds);
    }

    @Test
    void orderingAndPagingWorkCorrectly() {
        Instant now = Instant.now();
        agentRunRecordRepository.save(new AgentRunRecord(
                "run-a", "wf-page", null, null, null, "COMPLETED",
                now.minusSeconds(20), now, null, null, null));
        agentRunRecordRepository.save(new AgentRunRecord(
                "run-b", "wf-page", null, null, null, "COMPLETED",
                now.minusSeconds(10), now, null, null, null));
        agentRunRecordRepository.save(new AgentRunRecord(
                "run-c", "wf-page", null, null, null, "COMPLETED",
                now, now, null, null, null));

        List<AgentRunSummaryProjection> page =
                queryRepository.findSummariesByWorkflowId("wf-page", PageRequest.of(0, 2));

        assertThat(page).hasSize(2);
        assertThat(page.get(0).getRunId()).isEqualTo("run-c");
        assertThat(page.get(1).getRunId()).isEqualTo("run-b");
        assertThat(queryRepository.countByWorkflowId("wf-page")).isEqualTo(3);
    }
}
