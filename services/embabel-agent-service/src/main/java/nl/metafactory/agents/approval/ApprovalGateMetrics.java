package nl.metafactory.agents.approval;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * {@code openjcockpit_approval_paused_runs}: runs waiting at an approval gate (durable, so the value survives
 * a restart). Drives the "approvals waiting too long" alert in deploy/monitoring.
 */
@Configuration(proxyBeanMethods = false)
public class ApprovalGateMetrics {

    public ApprovalGateMetrics(ApprovalGateRegistry registry, MeterRegistry meterRegistry) {
        Gauge.builder("openjcockpit.approval.paused.runs", registry, ApprovalGateRegistry::size)
                .description("Workflow runs paused at an approval gate")
                .register(meterRegistry);
    }
}
