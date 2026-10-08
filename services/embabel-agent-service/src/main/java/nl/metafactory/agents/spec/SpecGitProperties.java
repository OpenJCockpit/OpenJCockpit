package nl.metafactory.agents.spec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("metafactory.spec-git")
public class SpecGitProperties {

    /** Publish specs produced by the requirement stage to git via the git MCP tools. */
    private boolean enabled = true;

    /** Base branch to branch off from. */
    private String baseBranch = "main";

    /** Prefix for spec branches; the workflow id and run id are appended. */
    private String branchPrefix = "spec";

    /** Prefix for implementation branches; the workflow id and run id are appended. */
    private String implBranchPrefix = "impl";

    /** Prefix for realisation (code change) branches; the workflow id and run id are appended. */
    private String realisationBranchPrefix = "feat";

    /** Directory in the repository where spec files are written. */
    private String specDirectory = "specs";

    /** Git username for clone/push; empty for anonymous access. */
    private String username = "";

    /** Git token or password for clone/push; empty for anonymous access. */
    private String token = "";

    /** Author name used for spec commits. */
    private String authorName = "Agentic Workflow";

    /** Author email used for spec commits. */
    private String authorEmail = "agentic@metafactory.nl";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getBranchPrefix() { return branchPrefix; }
    public void setBranchPrefix(String branchPrefix) { this.branchPrefix = branchPrefix; }
    public String getImplBranchPrefix() { return implBranchPrefix; }
    public void setImplBranchPrefix(String implBranchPrefix) { this.implBranchPrefix = implBranchPrefix; }
    public String getRealisationBranchPrefix() { return realisationBranchPrefix; }
    public void setRealisationBranchPrefix(String realisationBranchPrefix) { this.realisationBranchPrefix = realisationBranchPrefix; }
    public String getSpecDirectory() { return specDirectory; }
    public void setSpecDirectory(String specDirectory) { this.specDirectory = specDirectory; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
}
