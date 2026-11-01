package nl.metafactory.agents.approval;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalGateMetricsTest {

    @Test
    void thePausedRunGaugeReadsTheRegistry() {
        var registry = mock(ApprovalGateRegistry.class);
        when(registry.size()).thenReturn(3);
        var meters = new SimpleMeterRegistry();

        new ApprovalGateMetrics(registry, meters);

        assertThat(meters.get("openjcockpit.approval.paused.runs").gauge().value()).isEqualTo(3.0);
    }
}
