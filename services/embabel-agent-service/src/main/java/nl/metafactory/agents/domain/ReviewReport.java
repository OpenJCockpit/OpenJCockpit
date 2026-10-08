package nl.metafactory.agents.domain;

import java.util.ArrayList;
import java.util.List;

public record ReviewReport(String specId, boolean approved, List<String> findings) {

    public ReviewReport aggregate(ReviewReport other) {
        var merged = new ArrayList<>(this.findings);
        merged.addAll(other.findings);
        return new ReviewReport(this.specId, this.approved && other.approved, merged);
    }
}
