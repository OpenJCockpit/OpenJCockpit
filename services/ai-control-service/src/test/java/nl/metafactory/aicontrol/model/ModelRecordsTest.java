package nl.metafactory.aicontrol.model;

import nl.metafactory.aicontrol.client.AgentDefinitionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRecordsTest {

    @Test
    void agentDefinitionDto() {
        var r = new AgentDefinitionDto("requirement", "Requirement Agent", "desc", "specification",
                0, "SpecContent", "RequirementAnalysis");
        assertThat(r.id()).isEqualTo("requirement");
        assertThat(r.sequenceOrder()).isEqualTo(0);
        assertThat(r.inputType()).isEqualTo("SpecContent");
        assertThat(r.outputType()).isEqualTo("RequirementAnalysis");
    }

    @Test
    void agentCard() {
        var r = new AgentCard("id", "name", "desc", "Active", "active");
        assertThat(r.id()).isEqualTo("id");
        assertThat(r.name()).isEqualTo("name");
        assertThat(r.description()).isEqualTo("desc");
        assertThat(r.status()).isEqualTo("Active");
        assertThat(r.statusKind()).isEqualTo("active");
    }

    @Test
    void customerSummary() {
        var r = new CustomerSummary("id", "name", "sector", "env", "now", "Internal", "eu");
        assertThat(r.id()).isEqualTo("id");
        assertThat(r.region()).isEqualTo("eu");
    }

    @Test
    void dataClassificationValues() {
        assertThat(DataClassification.values()).hasSize(5);
    }

    @Test
    void evidenceDetails() {
        var r = new EvidenceDetails("v1", "run-1", "flow", "now");
        assertThat(r.specVersion()).isEqualTo("v1");
        assertThat(r.runId()).isEqualTo("run-1");
    }

    @Test
    void evidenceEvent() {
        var r = new EvidenceEvent("13:00", "title", "actor", "ok");
        assertThat(r.time()).isEqualTo("13:00");
        assertThat(r.status()).isEqualTo("ok");
    }

    @Test
    void footerStatus() {
        var r = new FooterStatus("Prod", "eu-west-1", "Internal", "1.0", "2.0");
        assertThat(r.environment()).isEqualTo("Prod");
        assertThat(r.springAiControlVersion()).isEqualTo("2.0");
    }

    @Test
    void modelRouteDecision() {
        var r = new ModelRouteDecision("CLOUD", "fast", true, "reason", List.of("audit"));
        assertThat(r.route()).isEqualTo("CLOUD");
        assertThat(r.cloudAllowed()).isTrue();
    }

    @Test
    void modelRouteRequest() {
        var r = new ModelRouteRequest("cust", "spec.md", DataClassification.L1_INTERNAL, "gen");
        assertThat(r.customerId()).isEqualTo("cust");
        assertThat(r.dataClassification()).isEqualTo(DataClassification.L1_INTERNAL);
    }

    @Test
    void pullRequestSummary() {
        var r = new PullRequestSummary("1", "title", "branch", "now", "Author", "Jan", "Open", "open");
        assertThat(r.id()).isEqualTo("1");
        assertThat(r.statusKind()).isEqualTo("open");
    }

    @Test
    void qualityControl() {
        var r = new QualityControl("SAST", "Compliant", "no issues");
        assertThat(r.name()).isEqualTo("SAST");
        assertThat(r.statusText()).isEqualTo("no issues");
    }

    @Test
    void specFile() {
        var r = new SpecFile("id", "file.md", "owner", "now", "Active", true,
                "# content", "https://github.com/org/repo");
        assertThat(r.id()).isEqualTo("id");
        assertThat(r.selected()).isTrue();
        assertThat(r.content()).isEqualTo("# content");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo");
    }

    @Test
    void stackServiceStatus() {
        var r = new StackServiceStatus("Service", "desc", "live");
        assertThat(r.name()).isEqualTo("Service");
        assertThat(r.status()).isEqualTo("live");
    }

    @Test
    void workspace() {
        var w = new Workspace(
            new CustomerSummary("id", "n", "s", "e", "t", "i", "r"),
            "spec.md", List.of(), List.of(), List.of(), List.of(),
            new EvidenceDetails("v", "r", "f", "t"),
            List.of(), List.of(),
            new FooterStatus("e", "r", "d", "ev", "sv")
        );
        assertThat(w.selectedSpecFile()).isEqualTo("spec.md");
    }
}
