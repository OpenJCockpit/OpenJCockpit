package nl.metafactory.aicontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agentic.workflow")
public class AgenticWorkflowProperties {

    private Container container = new Container();
    private Tmpfs tmpfs = new Tmpfs();
    private Preflight preflight = new Preflight();
    private int cloneTimeoutSeconds = 120;
    private int agentTimeoutSeconds = 600;
    private int pushTimeoutSeconds = 120;
    private boolean cleanupEnabled = true;
    private String defaultBaseBranch = "main";
    private String branchPrefix = "agentic";
    private int maxConcurrentJobs = 4;
    private String workspaceBasePath = System.getProperty("java.io.tmpdir") + "/agentic-workspaces";

    public static class Container {
        private boolean enabled = true;
        private String image = "eclipse-temurin:25-jre";
        private long memoryMb = 512;
        private String networkMode = "bridge";
        // Docker daemon endpoint, e.g. tcp://host.docker.internal:2375 when this service
        // itself runs in a container. Blank = docker-java default (DOCKER_HOST env or unix socket).
        private String dockerHost = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDockerHost() { return dockerHost; }
        public void setDockerHost(String dockerHost) { this.dockerHost = dockerHost; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        public long getMemoryMb() { return memoryMb; }
        public void setMemoryMb(long memoryMb) { this.memoryMb = memoryMb; }
        public String getNetworkMode() { return networkMode; }
        public void setNetworkMode(String networkMode) { this.networkMode = networkMode; }
    }

    public static class Tmpfs {
        private long sizeMb = 512;

        public long getSizeMb() { return sizeMb; }
        public void setSizeMb(long sizeMb) { this.sizeMb = sizeMb; }
    }

    public static class Preflight {
        private int dockerTimeoutSeconds = 5;

        public int getDockerTimeoutSeconds() { return dockerTimeoutSeconds; }
        public void setDockerTimeoutSeconds(int dockerTimeoutSeconds) { this.dockerTimeoutSeconds = dockerTimeoutSeconds; }
    }

    public Container getContainer() { return container; }
    public void setContainer(Container container) { this.container = container; }
    public Tmpfs getTmpfs() { return tmpfs; }
    public void setTmpfs(Tmpfs tmpfs) { this.tmpfs = tmpfs; }
    public Preflight getPreflight() { return preflight; }
    public void setPreflight(Preflight preflight) { this.preflight = preflight; }
    public int getCloneTimeoutSeconds() { return cloneTimeoutSeconds; }
    public void setCloneTimeoutSeconds(int v) { this.cloneTimeoutSeconds = v; }
    public int getAgentTimeoutSeconds() { return agentTimeoutSeconds; }
    public void setAgentTimeoutSeconds(int v) { this.agentTimeoutSeconds = v; }
    public int getPushTimeoutSeconds() { return pushTimeoutSeconds; }
    public void setPushTimeoutSeconds(int v) { this.pushTimeoutSeconds = v; }
    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public void setCleanupEnabled(boolean v) { this.cleanupEnabled = v; }
    public String getDefaultBaseBranch() { return defaultBaseBranch; }
    public void setDefaultBaseBranch(String v) { this.defaultBaseBranch = v; }
    public String getBranchPrefix() { return branchPrefix; }
    public void setBranchPrefix(String v) { this.branchPrefix = v; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int v) { this.maxConcurrentJobs = v; }
    public String getWorkspaceBasePath() { return workspaceBasePath; }
    public void setWorkspaceBasePath(String v) { this.workspaceBasePath = v; }
}
