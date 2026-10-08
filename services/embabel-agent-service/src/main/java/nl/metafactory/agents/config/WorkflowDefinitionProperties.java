package nl.metafactory.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("openjcockpit.workflow-definitions")
public class WorkflowDefinitionProperties {

    private String path = System.getProperty("java.io.tmpdir") + "/embabel-workflow-definitions";

    /** Import the default workflow bundle (spec init/create/implement) at startup. */
    private boolean seedDefaults = true;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public boolean isSeedDefaults() { return seedDefaults; }
    public void setSeedDefaults(boolean seedDefaults) { this.seedDefaults = seedDefaults; }
}
