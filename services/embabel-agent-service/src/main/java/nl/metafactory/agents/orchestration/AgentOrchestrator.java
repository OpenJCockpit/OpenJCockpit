package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;


public interface AgentOrchestrator {
    AgentRun start(AgentRunRequest request);

    /**
     * Starts a run bound to a git workspace (prepared by ai-control, redeemed from a ticket): the agents'
     * git tools work in that workspace and checkpoints commit and push its branch. {@code gitWorkspace} null is the same as {@link #start(AgentRunRequest)}.
     */
    default AgentRun start(AgentRunRequest request, nl.metafactory.agents.gitworkspace.GitWorkspaceBinding gitWorkspace) {
        if (gitWorkspace != null) {
            throw new UnsupportedOperationException("this orchestrator cannot bind runs to a git workspace");
        }
        return start(request);
    }

    AgentRun get(String runId);
    void stop(String runId);
}
