package nl.metafactory.agents.spec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("openjcockpit.spec-workflow")
public class SpecWorkflowProperties {

    /** Root folder that holds AGENTS.md, specs/ and templates/specs/. */
    private String basePath = ".";

    /** Scaffold and validate the spec structure at application startup. */
    private boolean initEnabled = true;

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public boolean isInitEnabled() { return initEnabled; }
    public void setInitEnabled(boolean initEnabled) { this.initEnabled = initEnabled; }
}
