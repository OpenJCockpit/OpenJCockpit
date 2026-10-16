package nl.metafactory.agents.orchestration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test double of the agent_run_outputs table. */
public class InMemoryAgentRunOutputs implements AgentRunOutputs {

    private final Map<String, Map<String, Output>> byRun = new ConcurrentHashMap<>();

    @Override
    public void record(String runId, String agentId, int position, String output) {
        byRun.computeIfAbsent(runId, r -> new ConcurrentHashMap<>()).put(agentId, new Output(agentId, position, output));
    }

    @Override
    public List<Output> find(String runId) {
        var list = new ArrayList<>(byRun.getOrDefault(runId, Map.of()).values());
        list.sort(Comparator.comparingInt(Output::position));
        return list;
    }
}
