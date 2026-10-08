package nl.metafactory.aicontrol.repository;

import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
@ActiveProfiles("test")
class ProjectGitCredentialRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectGitCredentialRepository credentialRepository;

    @BeforeEach
    void clearData() {
        credentialRepository.deleteAll();
        projectRepository.deleteAll();
        em.flush();
    }

    @Test
    void savesAndFindsActiveCredentialByProjectId() {
        var project = projectRepository.saveAndFlush(newProject());
        var cred = credential(project.getId(), (short) 1, "encrypted-token");
        credentialRepository.saveAndFlush(cred);
        em.clear();

        var found = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);

        assertThat(found).isPresent();
        assertThat(found.get().getEncryptedSecret()).isEqualTo("encrypted-token");
    }

    @Test
    void doesNotReturnInactiveCredential() {
        var project = projectRepository.saveAndFlush(newProject());
        credentialRepository.saveAndFlush(credential(project.getId(), (short) 0, "cipher"));
        em.clear();

        var found = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);

        assertThat(found).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoCredentialsExist() {
        var project = projectRepository.saveAndFlush(newProject());
        var found = credentialRepository
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1);
        assertThat(found).isEmpty();
    }

    @Test
    void setsCreatedAtAndUpdatedAtOnPersist() {
        var project = projectRepository.saveAndFlush(newProject());
        var saved = credentialRepository.saveAndFlush(credential(project.getId(), (short) 1, null));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCredentialType()).isEqualTo(GitCredentialType.GITHUB_PAT);
    }

    private Project newProject() {
        var p = new Project();
        p.setName("Cred Test Project");
        p.setActive((short) 1);
        return p;
    }

    private ProjectGitCredential credential(java.util.UUID projectId, short active, String encryptedSecret) {
        var c = new ProjectGitCredential();
        c.setProjectId(projectId);
        c.setActive(active);
        c.setCredentialType(GitCredentialType.GITHUB_PAT);
        c.setEncryptedSecret(encryptedSecret);
        return c;
    }
}
