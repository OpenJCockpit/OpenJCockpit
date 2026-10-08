package nl.metafactory.aicontrol.workflow;

import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;
import org.springframework.stereotype.Component;

/**
 * Default no-op FolderTriggerWatcher — intentionally does nothing until a real file-watching
 * implementation is added. Nothing currently calls watch(); it exists purely as the seam a future
 * implementation replaces.
 */
@Component
public class NoOpFolderTriggerWatcher implements FolderTriggerWatcher {

    @Override
    public void watch(ProjectDocumentFolderConfig config) {
        // Intentionally a no-op placeholder — see FolderTriggerWatcher javadoc.
    }
}
