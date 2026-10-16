package nl.metafactory.aicontrol.client;

/** One catalog agent's full output in a workflow run (embabel {@code GET /api/agent-runs/{id}/outputs}). */
public record AgentRunOutputDto(String agentId, int position, String output) {
}
