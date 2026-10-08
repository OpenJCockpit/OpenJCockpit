package nl.metafactory.agents.orchestration;

import nl.metafactory.agents.model.AgentDefinition;
import nl.metafactory.agents.model.AgentRun;
import nl.metafactory.agents.model.AgentRunRequest;

import java.util.List;

public interface AgentOrchestrator {
    List<AgentDefinition> availableAgents();
    AgentRun start(AgentRunRequest request);
    AgentRun get(String runId);
    void stop(String runId);
}
