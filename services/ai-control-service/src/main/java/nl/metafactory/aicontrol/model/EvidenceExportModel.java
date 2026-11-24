package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Neutral content of an export; every renderer works from this so all formats agree. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceExportModel(
        EvidenceExportScope scope,
        String workflowId,
        Instant exportedAt,
        List<EvidenceExportRun> runs
) {}
