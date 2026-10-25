package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.agentruntime.definition.config.AgentDefinitionProperties.SourceKind;
import nl.metafactory.agentruntime.definition.source.AgentDefinitionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Orders the available providers by the configured {@code source-priority}; absent kinds are skipped. */
public final class SourceOrder {

    private SourceOrder() {
    }

    public static List<AgentDefinitionProvider> order(Map<SourceKind, AgentDefinitionProvider> available,
                                                      List<SourceKind> priority) {
        var ordered = new ArrayList<AgentDefinitionProvider>();
        for (SourceKind kind : priority) {
            var provider = available.get(kind);
            if (provider != null) {
                ordered.add(provider);
            }
        }
        return List.copyOf(ordered);
    }
}
