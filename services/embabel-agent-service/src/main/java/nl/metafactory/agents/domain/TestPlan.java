package nl.metafactory.agents.domain;

import java.util.ArrayList;
import java.util.List;

public record TestPlan(String specId, List<String> testCases, String coverageTarget) {

    public TestPlan merge(TestPlan other) {
        var merged = new ArrayList<>(this.testCases);
        merged.addAll(other.testCases);
        return new TestPlan(this.specId, merged, this.coverageTarget);
    }
}
