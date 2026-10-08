package nl.metafactory.agents.workflow;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Narrower parser-only sanity check (architecture §2, §3.3): verifies that
 * {@code SpecGitPublisher.workflowId(...)} correctly extracts an id from a hand-constructed
 * {@code "workflow:" + id} string. This class never itself calls {@code WorkflowExecutionService}.
 *
 * The true end-to-end guarantee — that a real {@code AgentRunRequest} built by
 * {@code WorkflowExecutionService.doStart()} (via {@code startWorkflow(...)}) has its typed
 * {@code workflowId()} field and its string-encoded {@code requestedBy()} field agree with each
 * other on the same workflow id — is asserted by the test
 * {@code startsRunWithOnlyAgentIdsThatExistInTheCatalogue} in {@code WorkflowExecutionServiceTest}.
 */
class WorkflowIdEncodingDriftTest {

    private static String parseWorkflowId(String requestedBy) throws Exception {
        Method method = Class.forName("nl.metafactory.agents.spec.SpecGitPublisher")
                .getDeclaredMethod("workflowId", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, requestedBy);
    }

    @Test
    void parsedWorkflowIdAlwaysMatchesTheTypedFieldForASimpleId() throws Exception {
        String workflowId = "wf-1";
        String requestedBy = "workflow:" + workflowId;

        assertThat(parseWorkflowId(requestedBy)).isEqualTo(workflowId);
    }

    @Test
    void parsedWorkflowIdAlwaysMatchesTheTypedFieldForAGeneratedUuidStyleId() throws Exception {
        String workflowId = "wf-3f9c2b7a-1e4d-4a5b-9c3e-7a1b2c3d4e5f";
        String requestedBy = "workflow:" + workflowId;

        assertThat(parseWorkflowId(requestedBy)).isEqualTo(workflowId);
    }

    @Test
    void parsedWorkflowIdAlwaysMatchesTheTypedFieldForAnIdContainingHyphensAndDigits() throws Exception {
        String workflowId = "wf-onboarding-2026-09";
        String requestedBy = "workflow:" + workflowId;

        assertThat(parseWorkflowId(requestedBy)).isEqualTo(workflowId);
    }

    @Test
    void nonWorkflowRequestedByNeverProducesATypedWorkflowId() throws Exception {
        // Signal/file-triggered starts and the legacy unscoped POST /api/agent-runs endpoint use
        // requestedBy values that are NOT "workflow:"-prefixed; the parser must return null for
        // these, matching the typed workflowId field being null/absent in those same requests.
        assertThat(parseWorkflowId("agentic-workflow-job-abc123")).isNull();
        assertThat(parseWorkflowId("user1")).isNull();
        assertThat(parseWorkflowId(null)).isNull();
    }
}
