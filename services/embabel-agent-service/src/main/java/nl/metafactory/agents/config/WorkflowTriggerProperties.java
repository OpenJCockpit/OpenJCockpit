package nl.metafactory.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Every default here is also a Java field initialiser, so a truncated or absent
 * {@code openjcockpit.workflow-trigger} YAML block cannot produce a permissive value — the same
 * frozen-contract posture documented on {@link ApprovalGateProperties}. No
 * {@code openjcockpit.workflow-trigger} keys exist in any {@code application.yml} yet (that is a
 * separate, later work package); until then, these Java initialisers are the only source of
 * truth for this feature's configuration.
 */
@Component
@ConfigurationProperties("openjcockpit.workflow-trigger")
public class WorkflowTriggerProperties {

    private Duration sequentialMaxWait = Duration.ofMinutes(30);

    private int maxChainDepth = 3;

    private int maxOrbsPerWorkflow = 5;

    private boolean enabled = true;

    public Duration getSequentialMaxWait() {
        return sequentialMaxWait;
    }

    public void setSequentialMaxWait(Duration sequentialMaxWait) {
        this.sequentialMaxWait = sequentialMaxWait;
    }

    public int getMaxChainDepth() {
        return maxChainDepth;
    }

    public void setMaxChainDepth(int maxChainDepth) {
        this.maxChainDepth = maxChainDepth;
    }

    public int getMaxOrbsPerWorkflow() {
        return maxOrbsPerWorkflow;
    }

    public void setMaxOrbsPerWorkflow(int maxOrbsPerWorkflow) {
        this.maxOrbsPerWorkflow = maxOrbsPerWorkflow;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
