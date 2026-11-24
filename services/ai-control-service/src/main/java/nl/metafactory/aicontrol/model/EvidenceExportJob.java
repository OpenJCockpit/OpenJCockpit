package nl.metafactory.aicontrol.model;

import java.time.Instant;
import java.util.UUID;

public record EvidenceExportJob(
        UUID jobId,
        EvidenceExportJobStatus status,
        EvidenceExportProgress progress,
        String filename,
        Long sizeBytes,
        String error,
        Instant expiresAt
) {}
