package nl.metafactory.aicontrol.model;

public record ProjectDocumentFolderConfigRequest(
        String folderPath,
        boolean fileTriggerEnabled,
        String allowedDocumentTypes,
        String workflowId
) {}
