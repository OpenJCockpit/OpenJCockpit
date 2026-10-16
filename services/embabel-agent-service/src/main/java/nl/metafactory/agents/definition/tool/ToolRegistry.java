package nl.metafactory.agents.definition.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Every tool the server can execute for configuration-driven agents, keyed by tool id. */
public class ToolRegistry {

    private final Map<String, RegisteredAgentTool> byId;

    public ToolRegistry(List<RegisteredAgentTool> tools) {
        var map = new TreeMap<String, RegisteredAgentTool>();
        var llmNames = new TreeMap<String, String>();
        for (RegisteredAgentTool tool : tools) {
            if (map.putIfAbsent(tool.id(), tool) != null) {
                throw new IllegalStateException("duplicate tool id " + tool.id());
            }
            String clash = llmNames.putIfAbsent(RegisteredAgentTool.llmName(tool.id()), tool.id());
            if (clash != null) {
                throw new IllegalStateException("tool ids " + clash + " and " + tool.id() + " map to the same LLM name");
            }
        }
        this.byId = Map.copyOf(map);
    }

    public Optional<RegisteredAgentTool> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public java.util.Set<String> ids() {
        return byId.keySet();
    }
}
