package nl.metafactory.aicontrol.client;

import java.util.List;

public record WorkflowExportBundleDto(
        List<WorkflowGroupDto> groups,
        List<WorkflowDefinitionDto> workflows
) {
}
