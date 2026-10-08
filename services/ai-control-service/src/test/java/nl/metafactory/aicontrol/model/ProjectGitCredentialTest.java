package nl.metafactory.aicontrol.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectGitCredentialTest {

    @Test
    void gettersAndSetters() {
        var entity = new ProjectGitCredential();
        var id = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setCredentialType(GitCredentialType.GITHUB_PAT);
        entity.setUsername("user");
        entity.setEncryptedSecret("cipher");
        entity.setEncryptedPrivateKeyPassphrase("passphrase-cipher");
        entity.setGithubApiUrl("https://api.github.com");
        entity.setActive((short) 1);
        entity.setLastUsedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getProjectId()).isEqualTo(projectId);
        assertThat(entity.getCredentialType()).isEqualTo(GitCredentialType.GITHUB_PAT);
        assertThat(entity.getUsername()).isEqualTo("user");
        assertThat(entity.getEncryptedSecret()).isEqualTo("cipher");
        assertThat(entity.getEncryptedPrivateKeyPassphrase()).isEqualTo("passphrase-cipher");
        assertThat(entity.getGithubApiUrl()).isEqualTo("https://api.github.com");
        assertThat(entity.getActive()).isEqualTo((short) 1);
        assertThat(entity.getLastUsedAt()).isEqualTo(now);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void prePersistSetsCreatedAndUpdatedTimestamps() {
        var entity = new ProjectGitCredential();
        entity.prePersist();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void preUpdateRefreshesUpdatedAt() {
        var entity = new ProjectGitCredential();
        entity.prePersist();
        var originalUpdatedAt = entity.getUpdatedAt();

        entity.preUpdate();

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}
