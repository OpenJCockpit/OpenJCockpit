package nl.metafactory.aicontrol.model;

import java.util.List;

public record EvidenceRun(EvidenceRunDetails details, List<EvidenceTimelineEvent> events) {}
