package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.SourceStatus;

/** Whether a failed source may fall through to the next one. */
@FunctionalInterface
public interface FallbackPolicy {

    /** Never falls back: every non-FOUND status other than UNSUPPORTED ends the walk. */
    FallbackPolicy NONE = (binding, source, status) -> false;

    boolean mayContinueAfter(AgentTenantBinding binding, DefinitionSource failedSource, SourceStatus status);
}
