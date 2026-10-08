package nl.metafactory.aicontrol.workflow;

import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;

/**
 * Abstraction for watching a project's document folder and starting its configured workflow when a
 * file is delivered. Not wired to any real file-system watcher yet (see NoOpFolderTriggerWatcher) —
 * this exists so a real implementation (e.g. java.nio.file.WatchService or a storage-provider webhook)
 * can be dropped in later without touching callers of ProjectIntegrationConfigController.
 */
public interface FolderTriggerWatcher {

    void watch(ProjectDocumentFolderConfig config);
}
