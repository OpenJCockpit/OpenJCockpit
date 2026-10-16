package nl.metafactory.agents.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Local part of the agent catalog shown in the dashboard's Workflow design → Agents tab. The A2A
 * provider sections come from {@code openjcockpit.a2a-providers} ({@code A2AProviderClients}).
 */
@Component
@ConfigurationProperties("openjcockpit.agent-catalog")
public class AgentCatalogProperties {

    /** The repository-root {@code agents} folder (subfolders are the display hierarchy). */
    private String localPath = "../../agents";

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
}
