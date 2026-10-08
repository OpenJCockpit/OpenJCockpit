package nl.metafactory.agents.workflow.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowModelRecordsTest {

    @Test
    void mcpToolRef() {
        var r = new McpToolRef("search", "Searches the web");
        assertThat(r.name()).isEqualTo("search");
        assertThat(r.description()).isEqualTo("Searches the web");
    }

    @Test
    void skillSpec() {
        var tool = new McpToolRef("search", "desc");
        var r = new SkillSpec("summarize", "Summarizes text", "text", "summary",
                "Summarize the input", List.of(tool), "no PII");
        assertThat(r.name()).isEqualTo("summarize");
        assertThat(r.description()).isEqualTo("Summarizes text");
        assertThat(r.inputContract()).isEqualTo("text");
        assertThat(r.outputContract()).isEqualTo("summary");
        assertThat(r.executionInstructions()).isEqualTo("Summarize the input");
        assertThat(r.mcpTools()).containsExactly(tool);
        assertThat(r.policyNotes()).isEqualTo("no PII");
    }

    @Test
    void subagentSpec() {
        var r = new SubagentSpec("log-collector", "triage-agent", "Collects logs",
                "Gather relevant logs", "Query the log store", List.of("summarize"), List.of(), "wf-1");
        assertThat(r.name()).isEqualTo("log-collector");
        assertThat(r.parentAgent()).isEqualTo("triage-agent");
        assertThat(r.description()).isEqualTo("Collects logs");
        assertThat(r.responsibilities()).isEqualTo("Gather relevant logs");
        assertThat(r.instructions()).isEqualTo("Query the log store");
        assertThat(r.skillNames()).containsExactly("summarize");
        assertThat(r.mcpTools()).isEmpty();
        assertThat(r.workflowId()).isEqualTo("wf-1");
    }

    @Test
    void agentSpec() {
        var r = new AgentSpec("triage-agent", "Triages incoming issues", "triage",
                "Read the issue and classify it", List.of("log-collector"), List.of("summarize"), List.of(), "wf-1", false);
        assertThat(r.name()).isEqualTo("triage-agent");
        assertThat(r.description()).isEqualTo("Triages incoming issues");
        assertThat(r.role()).isEqualTo("triage");
        assertThat(r.instructions()).isEqualTo("Read the issue and classify it");
        assertThat(r.subagentNames()).containsExactly("log-collector");
        assertThat(r.skillNames()).containsExactly("summarize");
        assertThat(r.mcpTools()).isEmpty();
        assertThat(r.workflowId()).isEqualTo("wf-1");
        assertThat(r.builtIn()).isFalse();
    }

    @Test
    void triggerConfig() {
        var r = new TriggerConfig(true, true, "issue.created", false, null);
        assertThat(r.dashboardButtonEnabled()).isTrue();
        assertThat(r.hermesSignalEnabled()).isTrue();
        assertThat(r.hermesSignalType()).isEqualTo("issue.created");
        assertThat(r.fileDeliveryEnabled()).isFalse();
        assertThat(r.fileDeliveryFolderPath()).isNull();
    }

    @Test
    void executionConfig() {
        var r = new ExecutionConfig("cust1", "https://github.com/org/repo", 600);
        assertThat(r.customerId()).isEqualTo("cust1");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(r.timeoutSeconds()).isEqualTo(600);
    }

    @Test
    void workflowDefinition() {
        var trigger = new TriggerConfig(true, false, null, false, null);
        var execution = new ExecutionConfig("cust1", "", 300);
        var now = Instant.now();
        var r = new WorkflowDefinition("wf-1", "Onboarding", "Noordzee Logistics", "wg-1", "desc",
                List.of("requirement"), List.of("log-collector"), List.of("summarize"), List.of(),
                trigger, execution, true, "Create a branch first.", "ACTIVE", "COMPLETED", now, null, null);

        assertThat(r.id()).isEqualTo("wf-1");
        assertThat(r.name()).isEqualTo("Onboarding");
        assertThat(r.projectName()).isEqualTo("Noordzee Logistics");
        assertThat(r.groupId()).isEqualTo("wg-1");
        assertThat(r.description()).isEqualTo("desc");
        assertThat(r.agentIds()).containsExactly("requirement");
        assertThat(r.subagentNames()).containsExactly("log-collector");
        assertThat(r.skillNames()).containsExactly("summarize");
        assertThat(r.mcpTools()).isEmpty();
        assertThat(r.trigger()).isEqualTo(trigger);
        assertThat(r.execution()).isEqualTo(execution);
        assertThat(r.promptRequired()).isTrue();
        assertThat(r.promptInstructions()).isEqualTo("Create a branch first.");
        assertThat(r.status()).isEqualTo("ACTIVE");
        assertThat(r.lastExecutionStatus()).isEqualTo("COMPLETED");
        assertThat(r.lastExecutionAt()).isEqualTo(now);
    }

    @Test
    void workflowGroup() {
        var r = new WorkflowGroup("wg-1", "Onboarding flows", "Workflows around customer onboarding", "Noordzee Logistics");
        assertThat(r.id()).isEqualTo("wg-1");
        assertThat(r.name()).isEqualTo("Onboarding flows");
        assertThat(r.description()).isEqualTo("Workflows around customer onboarding");
        assertThat(r.projectName()).isEqualTo("Noordzee Logistics");
    }

    @Test
    void workflowStartInput() {
        var r = new WorkflowStartInput("Create a spec for login", "001-example-feature/spec.md",
                "https://github.com/org/repo.git", "bot", "secret", "develop");
        assertThat(r.prompt()).isEqualTo("Create a spec for login");
        assertThat(r.specFile()).isEqualTo("001-example-feature/spec.md");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(r.gitUsername()).isEqualTo("bot");
        assertThat(r.gitToken()).isEqualTo("secret");
        assertThat(r.baseBranch()).isEqualTo("develop");
        assertThat(WorkflowStartInput.empty().prompt()).isNull();
        assertThat(WorkflowStartInput.empty().specFile()).isNull();
        assertThat(WorkflowStartInput.empty().repositoryUrl()).isNull();
        assertThat(WorkflowStartInput.empty().gitUsername()).isNull();
        assertThat(WorkflowStartInput.empty().gitToken()).isNull();
        assertThat(WorkflowStartInput.empty().baseBranch()).isNull();
    }

    @Test
    void workflowExportBundleAndImportResult() {
        var group = new WorkflowGroup("wg-1", "Onboarding flows", "desc", null);
        var bundle = new WorkflowExportBundle(List.of(group), List.of());
        assertThat(bundle.groups()).containsExactly(group);
        assertThat(bundle.workflows()).isEmpty();

        var result = new WorkflowImportResult(1, 2, List.of());
        assertThat(result.groupsImported()).isEqualTo(1);
        assertThat(result.workflowsImported()).isEqualTo(2);
        assertThat(result.violations()).isEmpty();
    }
}
