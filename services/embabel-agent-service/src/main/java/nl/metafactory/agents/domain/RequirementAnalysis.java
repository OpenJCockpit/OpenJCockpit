package nl.metafactory.agents.domain;

import java.util.List;

public record RequirementAnalysis(String specId, List<String> requirements, String summary) {}
