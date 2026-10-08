package nl.metafactory.aicontrol.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowClientDtoRecordsTest {

    @Test
    void approvalGateCommentDto() {
        var now = Instant.now();
        var r = new ApprovalGateCommentDto(1, "alice", "please rename the variable", now);
        assertThat(r.iteration()).isEqualTo(1);
        assertThat(r.author()).isEqualTo("alice");
        assertThat(r.comment()).isEqualTo("please rename the variable");
        assertThat(r.submittedAt()).isEqualTo(now);
    }

    @Test
    void mcpToolRefDto() {
        var r = new McpToolRefDto("search", "Searches the web");
        assertThat(r.name()).isEqualTo("search");
        assertThat(r.description()).isEqualTo("Searches the web");
    }

    @Test
    void workflowOrbDto() {
        var r = new WorkflowOrbDto("wf-2", WorkflowOrbMode.SEQUENTIAL, "impact");
        assertThat(r.workflowId()).isEqualTo("wf-2");
        assertThat(r.mode()).isEqualTo(WorkflowOrbMode.SEQUENTIAL);
        assertThat(r.placementStage()).isEqualTo("impact");
    }

    @Test
    void triggerConfigDto() {
        var r = new TriggerConfigDto(true, true, "issue.created", false, null);
        assertThat(r.dashboardButtonEnabled()).isTrue();
        assertThat(r.hermesSignalEnabled()).isTrue();
        assertThat(r.hermesSignalType()).isEqualTo("issue.created");
        assertThat(r.fileDeliveryEnabled()).isFalse();
        assertThat(r.fileDeliveryFolderPath()).isNull();
    }

    @Test
    void executionConfigDto() {
        var r = new ExecutionConfigDto("cust1", "https://github.com/org/repo", 600);
        assertThat(r.customerId()).isEqualTo("cust1");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(r.timeoutSeconds()).isEqualTo(600);
    }

    @Test
    void workflowStartInputDto() {
        var r = new WorkflowStartInputDto("Create a spec", "001-example-feature/spec.md",
                "https://github.com/org/repo.git", "11111111-2222-3333-4444-555555555555", "bot", "secret", "develop");
        assertThat(r.prompt()).isEqualTo("Create a spec");
        assertThat(r.specFile()).isEqualTo("001-example-feature/spec.md");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(r.projectId()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(r.gitUsername()).isEqualTo("bot");
        assertThat(r.gitToken()).isEqualTo("secret");
        assertThat(r.baseBranch()).isEqualTo("develop");
        assertThat(WorkflowStartInputDto.empty().prompt()).isNull();
        assertThat(WorkflowStartInputDto.empty().specFile()).isNull();
        assertThat(WorkflowStartInputDto.empty().repositoryUrl()).isNull();
        assertThat(WorkflowStartInputDto.empty().projectId()).isNull();
        assertThat(WorkflowStartInputDto.empty().gitUsername()).isNull();
        assertThat(WorkflowStartInputDto.empty().gitToken()).isNull();
        assertThat(WorkflowStartInputDto.empty().baseBranch()).isNull();
    }

    @Test
    void workflowGroupDto() {
        var r = new WorkflowGroupDto("wg-1", "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
        assertThat(r.id()).isEqualTo("wg-1");
        assertThat(r.name()).isEqualTo("Onboarding flows");
        assertThat(r.description()).isEqualTo("Workflows around customer onboarding");
        assertThat(r.projectName()).isEqualTo("Noordzee Logistics");
    }

    @Test
    void workflowExportBundleAndImportResultDto() {
        var group = new WorkflowGroupDto("wg-1", "Onboarding flows", "desc", null);
        var bundle = new WorkflowExportBundleDto(java.util.List.of(group), java.util.List.of());
        assertThat(bundle.groups()).containsExactly(group);
        assertThat(bundle.workflows()).isEmpty();

        var result = new WorkflowImportResultDto(1, 2, java.util.List.of("workflow orb references unknown workflow 'wf-missing'"));
        assertThat(result.groupsImported()).isEqualTo(1);
        assertThat(result.workflowsImported()).isEqualTo(2);
        assertThat(result.violations()).containsExactly("workflow orb references unknown workflow 'wf-missing'");
    }
}
