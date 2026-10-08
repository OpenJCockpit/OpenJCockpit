package nl.metafactory.agents.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record EvidenceBundle(String specId, List<EvidenceEntry> entries, Instant completedAt) {

    public static EvidenceBundle from(RequirementAnalysis ra, ImpactReport ir,
                                      TestPlan tp, ImplementationPlan ip, ReviewReport rr) {
        var entries = List.of(
            new EvidenceEntry(Instant.now(), "requirement", "requirements-analyzed", ra.summary()),
            new EvidenceEntry(Instant.now(), "impact", "impact-assessed", ir.riskLevel()),
            new EvidenceEntry(Instant.now(), "test-design", "tests-designed", tp.coverageTarget()),
            new EvidenceEntry(Instant.now(), "implementation", "plan-created", ip.architectureDecision()),
            new EvidenceEntry(Instant.now(), "review", "review-completed", String.valueOf(rr.approved()))
        );
        return new EvidenceBundle(ra.specId(), entries, Instant.now());
    }

    public EvidenceBundle withAdditionalEntries(List<EvidenceEntry> additional) {
        var merged = new ArrayList<>(this.entries);
        merged.addAll(additional);
        return new EvidenceBundle(this.specId, merged, this.completedAt);
    }
}
