package nl.metafactory.agents.workflow;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.agents.workflow.model.WorkflowDefinition;
import nl.metafactory.agents.workflow.model.WorkflowExportBundle;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
@Component
public class DefaultWorkflowBundleReader {
    static final String DEFAULT_BUNDLE_RESOURCE = "spec-workflow/default-workflows.json";
    // FAIL_ON_UNKNOWN_PROPERTIES disabled (ADR-008, batch B1): this ApplicationRunner reads the
    // bundle at boot, so an unrecognised property (e.g. a gate field on a definition saved by a
    // newer build) must be ignored rather than crashing application startup on rollback.
    // FAIL_ON_NULL_FOR_PRIMITIVES disabled: a workflow bundle written by an older build (or a
    // hand-authored test fixture) may omit a primitive boolean field entirely, and it must default
    // to false rather than fail bundle import, matching this class's existing tolerant-rollback
    // posture for unknown properties.
    private final JsonMapper objectMapper = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    WorkflowExportBundle readBundle() {
        InputStream in = openBundleResource();
        if (in == null) {
            throw new IllegalStateException("Default workflow bundle missing on classpath: " + DEFAULT_BUNDLE_RESOURCE);
        }
        try {
            return objectMapper.readValue(in, WorkflowExportBundle.class);
        } catch (tools.jackson.core.JacksonException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
    InputStream openBundleResource() { return getClass().getClassLoader().getResourceAsStream(DEFAULT_BUNDLE_RESOURCE); }
    Optional<WorkflowDefinition> workflowById(WorkflowExportBundle bundle, String id) { if (bundle.workflows() == null) { return Optional.empty(); } return bundle.workflows().stream().filter(w -> Objects.equals(w.id(), id)).findFirst(); }
}
