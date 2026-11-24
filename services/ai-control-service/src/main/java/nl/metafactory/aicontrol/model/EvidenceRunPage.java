package nl.metafactory.aicontrol.model;

import java.util.List;

public record EvidenceRunPage(List<EvidenceRunSummary> items, long total, int limit, int offset) {}
