package nl.metafactory.agents.domain;

import java.util.ArrayList;
import java.util.List;

public record ImplementationPlan(String specId, List<String> proposedChanges, String architectureDecision) {

    public ImplementationPlan merge(ImplementationPlan other) {
        var merged = new ArrayList<>(this.proposedChanges);
        merged.addAll(other.proposedChanges);
        var arch = this.architectureDecision.isBlank() ? other.architectureDecision : this.architectureDecision;
        return new ImplementationPlan(this.specId, merged, arch);
    }
}
