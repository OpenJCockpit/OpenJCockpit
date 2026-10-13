package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "git_url")
    private String gitUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_branch")
    private String defaultBranch;

    private String environment;

    private String owner;

    @Column(name = "new_project", nullable = false)
    private short newProject = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "git_status", nullable = false, length = 20)
    private GitStatus gitStatus = GitStatus.UNKNOWN;

    @Column(name = "git_status_checked_at")
    private Instant gitStatusCheckedAt;

    @Column(name = "git_status_message", length = 500)
    private String gitStatusMessage;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getGitUrl() { return gitUrl; }
    public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public short getNewProject() { return newProject; }
    public void setNewProject(short newProject) { this.newProject = newProject; }

    public GitStatus getGitStatus() { return gitStatus; }
    public void setGitStatus(GitStatus gitStatus) { this.gitStatus = gitStatus; }

    public Instant getGitStatusCheckedAt() { return gitStatusCheckedAt; }
    public void setGitStatusCheckedAt(Instant gitStatusCheckedAt) { this.gitStatusCheckedAt = gitStatusCheckedAt; }

    public String getGitStatusMessage() { return gitStatusMessage; }
    public void setGitStatusMessage(String gitStatusMessage) { this.gitStatusMessage = gitStatusMessage; }
}
