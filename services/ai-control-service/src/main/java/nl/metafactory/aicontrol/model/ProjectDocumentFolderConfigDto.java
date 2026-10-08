package nl.metafactory.aicontrol.model;

import java.util.UUID;

/**
 * folderName always mirrors the project's name (see Project.name) — computed at read time,
 * never stored, so renaming a project doesn't require migrating this config.
 */
public record ProjectDocumentFolderConfigDto(
        UUID projectId,
        String projectName,
        String folderName,
        String folderPath,
        boolean fileTriggerEnabled,
        String allowedDocumentTypes,
        String workflowId
) {}
