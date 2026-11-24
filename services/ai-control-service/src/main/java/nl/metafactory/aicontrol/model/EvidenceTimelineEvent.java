package nl.metafactory.aicontrol.model;

import java.time.Instant;

public record EvidenceTimelineEvent(
        Instant timestamp,
        EvidenceSource source,
        String agentId,
        String actor,
        String title,
        String detail,
        String outputRef,
        EvidenceStatus status
) {}
