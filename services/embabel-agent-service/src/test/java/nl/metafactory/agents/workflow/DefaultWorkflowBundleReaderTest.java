package nl.metafactory.agents.workflow;

import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultWorkflowBundleReaderTest {
    @Test void workflowByIdFindsAMatchingWorkflow() { var def = new WorkflowDefinition("wf-a", "Name", null, null, null, List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null); var bundle = new WorkflowExportBundle(List.of(), List.of(def)); var reader = new DefaultWorkflowBundleReader(); assertThat(reader.workflowById(bundle, "wf-a")).isPresent().get().extracting(WorkflowDefinition::id).isEqualTo("wf-a"); }
    @Test void workflowByIdReturnsEmptyWhenNoMatch() { var def = new WorkflowDefinition("wf-a", "Name", null, null, null, List.of(), List.of(), List.of(), List.of(), null, null, false, null, "ACTIVE", null, null, null, null); var bundle = new WorkflowExportBundle(List.of(), List.of(def)); var reader = new DefaultWorkflowBundleReader(); assertThat(reader.workflowById(bundle, "wf-missing")).isEmpty(); }
    @Test void workflowByIdReturnsEmptyWhenWorkflowsListIsNull() { var bundle = new WorkflowExportBundle(List.of(), null); var reader = new DefaultWorkflowBundleReader(); assertThat(reader.workflowById(bundle, "wf-a")).isEmpty(); }
    @Test void readBundleWrapsRealIOExceptionFromStreamInUncheckedIOException() {
        var reader = new DefaultWorkflowBundleReader() {
            @Override InputStream openBundleResource() {
                return new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("broken stream"); }
                };
            }
        };
        assertThatThrownBy(reader::readBundle).isInstanceOf(UncheckedIOException.class);
    }
}
