package nl.metafactory.agents.definition.tool;

import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ResolutionFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bind-time tool authorisation. A tool is only bound when it is (1) registered on this server and
 * (2) in the server-side allowlist for this agent and tenant. The definition's own tool list can
 * only narrow that set, never widen it; skill {@code allowed-tools} declarations are ignored.
 */
public class ToolAccessPolicy {

    private final ToolRegistry registry;

    public ToolAccessPolicy(ToolRegistry registry) {
        this.registry = registry;
    }

    public List<RegisteredAgentTool> authorize(String agentId, List<String> requested, Set<String> serverAllowlist) {
        var bound = new ArrayList<RegisteredAgentTool>();
        for (String id : requested) {
            var tool = registry.find(id).orElseThrow(() -> new AgentResolutionException(ResolutionFailure.TOOL_DENIED,
                    "agent " + agentId + " requests unregistered tool " + id));
            if (serverAllowlist == null || !serverAllowlist.contains(id)) {
                throw new AgentResolutionException(ResolutionFailure.TOOL_DENIED,
                        "agent " + agentId + " is not allowed to use tool " + id);
            }
            bound.add(tool);
        }
        return List.copyOf(bound);
    }
}
