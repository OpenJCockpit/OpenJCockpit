package nl.metafactory.aicontrol.model;

import java.time.Instant;

/** Request to export all runs of a workflow ({@code workflowId} required) or of the whole project. */
public record EvidenceExportRequest(
        EvidenceExportScope scope,
        String workflowId,
        EvidenceExportFormat format,
        Boolean includeOutputs,
        Instant from,
        Instant to
) {}
