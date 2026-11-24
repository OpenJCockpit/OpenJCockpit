package nl.metafactory.aicontrol.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One run in an export. {@code outputs} is null unless outputs were requested; {@code error} is set
 * (with null details and no events) when the run could not be loaded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceExportRun(
        String runId,
        EvidenceRunDetails details,
        List<EvidenceTimelineEvent> events,
        List<EvidenceExportOutput> outputs,
        boolean outputsUnavailable,
        String error
) {
    public static EvidenceExportRun failed(String runId, String error) {
        return new EvidenceExportRun(runId, null, List.of(), null, false, error);
    }
}
