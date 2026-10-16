package nl.metafactory.agents.orchestration;

import java.util.List;

/** Durable full outputs of the agents of a catalog workflow run, in execution order. */
public interface AgentRunOutputs {

    record Output(String agentId, int position, String output) {
    }

    /** Max characters stored per agent output. */
    int MAX_OUTPUT_CHARS = 1_000_000;

    /** Stores (or, after a feedback re-run, replaces) one agent's output. */
    void record(String runId, String agentId, int position, String output);

    List<Output> find(String runId);
}
