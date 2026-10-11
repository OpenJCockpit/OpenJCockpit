package nl.metafactory.aicontrol.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "project_git_credentials")
public class ProjectGitCredential extends ProjectScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 30)
    private GitCredentialType credentialType = GitCredentialType.NONE;

    private String username;

    @Column(name = "encrypted_secret", columnDefinition = "TEXT")
    private String encryptedSecret;

    @Column(name = "encrypted_private_key_passphrase", columnDefinition = "TEXT")
    private String encryptedPrivateKeyPassphrase;

    @Column(name = "github_api_url", length = 500)
    private String githubApiUrl;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public GitCredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(GitCredentialType credentialType) { this.credentialType = credentialType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncryptedSecret() { return encryptedSecret; }
    public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }

    public String getEncryptedPrivateKeyPassphrase() { return encryptedPrivateKeyPassphrase; }
    public void setEncryptedPrivateKeyPassphrase(String passphrase) { this.encryptedPrivateKeyPassphrase = passphrase; }

    public String getGithubApiUrl() { return githubApiUrl; }
    public void setGithubApiUrl(String githubApiUrl) { this.githubApiUrl = githubApiUrl; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
