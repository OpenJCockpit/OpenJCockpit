package nl.metafactory.agents.agent;

import nl.metafactory.agents.domain.*;
import nl.metafactory.agents.subagent.ActionCollectorAgent;
import nl.metafactory.agents.subagent.EventCollectorAgent;
import nl.metafactory.agents.subagent.LogCollectorAgent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceAgentTest {

    @Test
    void compileEvidenceBuildsAndEnrichesBundle() {
        var logCollector = mock(LogCollectorAgent.class);
        var actionCollector = mock(ActionCollectorAgent.class);
        var eventCollector = mock(EventCollectorAgent.class);

        var finalBundle = new EvidenceBundle("id",
            List.of(new EvidenceEntry(Instant.now(), "event-collector", "events-captured", "done")),
            Instant.now());
        when(logCollector.collectLogs(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actionCollector.collectActions(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventCollector.collectEvents(any())).thenReturn(finalBundle);

        var agent = new EvidenceAgent(logCollector, actionCollector, eventCollector);
        var result = agent.compileEvidence(
            new RequirementAnalysis("id", List.of("r"), "sum"),
            new ImpactReport("id", List.of("m"), "LOW"),
            new TestPlan("id", List.of("t"), "90%"),
            new ImplementationPlan("id", List.of("c"), "arch"),
            new ReviewReport("id", true, List.of())
        );

        assertThat(result).isEqualTo(finalBundle);
    }
}
