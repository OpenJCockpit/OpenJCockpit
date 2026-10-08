package nl.metafactory.aicontrol.client;

import java.util.List;

public record WorkflowImportResultDto(
        int groupsImported,
        int workflowsImported,
        List<String> violations
) {
}
