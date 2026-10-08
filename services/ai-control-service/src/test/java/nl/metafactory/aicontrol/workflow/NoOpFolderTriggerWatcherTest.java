package nl.metafactory.aicontrol.workflow;

import nl.metafactory.aicontrol.model.ProjectDocumentFolderConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpFolderTriggerWatcherTest {

    @Test
    void watchDoesNothingAndNeverThrows() {
        var watcher = new NoOpFolderTriggerWatcher();

        assertThatCode(() -> watcher.watch(new ProjectDocumentFolderConfig())).doesNotThrowAnyException();
    }
}
