package nl.metafactory.agents.workflow.agents;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** How workflow runs execute the catalog agents they reference. */
@Component
@ConfigurationProperties("openjcockpit.agent-workflows")
public class AgentWorkflowProperties {

    /**
     * Tenant whose agent bindings apply to local (in-process) agents in workflow runs. When unset the
     * workflow's customer id is used. A2A agents get their tenant from the caller token instead.
     */
    private String tenant;

    /** Max characters of the message sent to one agent (input + previous outputs + feedback). */
    private int maxMessageChars = 30_000;

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public int getMaxMessageChars() {
        return maxMessageChars;
    }

    public void setMaxMessageChars(int maxMessageChars) {
        this.maxMessageChars = maxMessageChars;
    }
}
