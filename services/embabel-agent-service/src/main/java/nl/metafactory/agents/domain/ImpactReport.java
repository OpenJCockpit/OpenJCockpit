package nl.metafactory.agents.domain;

import java.util.List;

public record ImpactReport(String specId, List<String> affectedAreas, String riskLevel) {}
