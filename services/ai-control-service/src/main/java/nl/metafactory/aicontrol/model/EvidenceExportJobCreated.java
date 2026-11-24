package nl.metafactory.aicontrol.model;

import java.util.UUID;

public record EvidenceExportJobCreated(UUID jobId, EvidenceExportJobStatus status) {}
