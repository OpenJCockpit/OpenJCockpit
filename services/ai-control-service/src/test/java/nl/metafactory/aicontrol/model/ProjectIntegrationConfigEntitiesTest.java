package nl.metafactory.aicontrol.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectIntegrationConfigEntitiesTest {

    @Test
    void hermesConfigGettersAndSetters() {
        var entity = new ProjectHermesConfig();
        var id = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setEnabled((short) 1);
        entity.setEndpointUrl("https://hermes.example.com");
        entity.setEncryptedAuthToken("cipher");
        entity.setSignalType("issue.created");
        entity.setWorkflowId("wf-1");
        entity.setActive((short) 1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getProjectId()).isEqualTo(projectId);
        assertThat(entity.getEnabled()).isEqualTo((short) 1);
        assertThat(entity.getEndpointUrl()).isEqualTo("https://hermes.example.com");
        assertThat(entity.getEncryptedAuthToken()).isEqualTo("cipher");
        assertThat(entity.getSignalType()).isEqualTo("issue.created");
        assertThat(entity.getWorkflowId()).isEqualTo("wf-1");
        assertThat(entity.getActive()).isEqualTo((short) 1);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);

        entity.prePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        entity.preUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void jiraConfigGettersAndSetters() {
        var entity = new ProjectJiraConfig();
        var id = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setEnabled((short) 1);
        entity.setBaseUrl("https://noordzee.atlassian.net");
        entity.setProjectKey("NL");
        entity.setEncryptedAuthToken("cipher");
        entity.setIssueTypeMapping("Bug=bugfix-workflow");
        entity.setWorkflowId("wf-1");
        entity.setActive((short) 1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getProjectId()).isEqualTo(projectId);
        assertThat(entity.getEnabled()).isEqualTo((short) 1);
        assertThat(entity.getBaseUrl()).isEqualTo("https://noordzee.atlassian.net");
        assertThat(entity.getProjectKey()).isEqualTo("NL");
        assertThat(entity.getEncryptedAuthToken()).isEqualTo("cipher");
        assertThat(entity.getIssueTypeMapping()).isEqualTo("Bug=bugfix-workflow");
        assertThat(entity.getWorkflowId()).isEqualTo("wf-1");
        assertThat(entity.getActive()).isEqualTo((short) 1);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);

        entity.prePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        entity.preUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void documentFolderConfigGettersAndSetters() {
        var entity = new ProjectDocumentFolderConfig();
        var id = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var now = Instant.now();

        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setFolderPath("/documents");
        entity.setFileTriggerEnabled((short) 1);
        entity.setAllowedDocumentTypes("pdf,docx");
        entity.setWorkflowId("wf-1");
        entity.setActive((short) 1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getProjectId()).isEqualTo(projectId);
        assertThat(entity.getFolderPath()).isEqualTo("/documents");
        assertThat(entity.getFileTriggerEnabled()).isEqualTo((short) 1);
        assertThat(entity.getAllowedDocumentTypes()).isEqualTo("pdf,docx");
        assertThat(entity.getWorkflowId()).isEqualTo("wf-1");
        assertThat(entity.getActive()).isEqualTo((short) 1);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);

        entity.prePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        entity.preUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}
