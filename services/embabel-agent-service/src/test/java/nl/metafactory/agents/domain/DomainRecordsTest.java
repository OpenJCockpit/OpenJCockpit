package nl.metafactory.agents.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainRecordsTest {

    @Test
    void specContent() {
        var r = new SpecContent("id", "file.md", "content", "https://github.com/org/repo");
        assertThat(r.specId()).isEqualTo("id");
        assertThat(r.fileName()).isEqualTo("file.md");
        assertThat(r.content()).isEqualTo("content");
        assertThat(r.repositoryUrl()).isEqualTo("https://github.com/org/repo");
    }

    @Test
    void requirementAnalysis() {
        var r = new RequirementAnalysis("id", List.of("req1"), "summary");
        assertThat(r.specId()).isEqualTo("id");
        assertThat(r.requirements()).containsExactly("req1");
        assertThat(r.summary()).isEqualTo("summary");
    }

    @Test
    void fileSelectionAndCodeChangeSet() {
        var selection = new FileSelection(List.of("src/App.java"));
        assertThat(selection.paths()).containsExactly("src/App.java");

        var change = new FileChange("src/App.java", "class App {}");
        assertThat(change.path()).isEqualTo("src/App.java");
        assertThat(change.content()).isEqualTo("class App {}");

        var changeSet = new CodeChangeSet("summary", List.of(change));
        assertThat(changeSet.summary()).isEqualTo("summary");
        assertThat(changeSet.changes()).containsExactly(change);
    }

    @Test
    void impactReport() {
        var r = new ImpactReport("id", List.of("module-a"), "HIGH");
        assertThat(r.specId()).isEqualTo("id");
        assertThat(r.affectedAreas()).containsExactly("module-a");
        assertThat(r.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void testPlanMerge() {
        var a = new TestPlan("id", List.of("test1"), "90%");
        var b = new TestPlan("id", List.of("test2"), "80%");
        var merged = a.merge(b);
        assertThat(merged.testCases()).containsExactly("test1", "test2");
        assertThat(merged.coverageTarget()).isEqualTo("90%");
        assertThat(merged.specId()).isEqualTo("id");
    }

    @Test
    void implementationPlanMergeUsesThisArchitectureWhenNotBlank() {
        var a = new ImplementationPlan("id", List.of("change1"), "microservices");
        var b = new ImplementationPlan("id", List.of("change2"), "monolith");
        var merged = a.merge(b);
        assertThat(merged.proposedChanges()).containsExactly("change1", "change2");
        assertThat(merged.architectureDecision()).isEqualTo("microservices");
    }

    @Test
    void implementationPlanMergeUsesOtherArchitectureWhenThisIsBlank() {
        var a = new ImplementationPlan("id", List.of("change1"), "");
        var b = new ImplementationPlan("id", List.of("change2"), "event-driven");
        var merged = a.merge(b);
        assertThat(merged.architectureDecision()).isEqualTo("event-driven");
    }

    @Test
    void reviewReportAggregate() {
        var a = new ReviewReport("id", true, List.of("finding1"));
        var b = new ReviewReport("id", false, List.of("finding2"));
        var agg = a.aggregate(b);
        assertThat(agg.approved()).isFalse();
        assertThat(agg.findings()).containsExactly("finding1", "finding2");
        assertThat(agg.specId()).isEqualTo("id");
    }

    @Test
    void evidenceEntry() {
        var ts = Instant.now();
        var r = new EvidenceEntry(ts, "agent-1", "action", "OK");
        assertThat(r.timestamp()).isEqualTo(ts);
        assertThat(r.agentId()).isEqualTo("agent-1");
        assertThat(r.action()).isEqualTo("action");
        assertThat(r.outcome()).isEqualTo("OK");
    }

    @Test
    void evidenceBundleFrom() {
        var ra = new RequirementAnalysis("id", List.of("r1"), "summary");
        var ir = new ImpactReport("id", List.of("m1"), "LOW");
        var tp = new TestPlan("id", List.of("t1"), "95%");
        var ip = new ImplementationPlan("id", List.of("c1"), "arch");
        var rr = new ReviewReport("id", true, List.of());
        var bundle = EvidenceBundle.from(ra, ir, tp, ip, rr);
        assertThat(bundle.specId()).isEqualTo("id");
        assertThat(bundle.entries()).hasSize(5);
        assertThat(bundle.completedAt()).isNotNull();
    }

    @Test
    void evidenceBundleWithAdditionalEntries() {
        var base = new EvidenceBundle("id", List.of(), Instant.now());
        var extra = new EvidenceEntry(Instant.now(), "a", "b", "c");
        var extended = base.withAdditionalEntries(List.of(extra));
        assertThat(extended.entries()).hasSize(1);
        assertThat(extended.specId()).isEqualTo("id");
    }
}
