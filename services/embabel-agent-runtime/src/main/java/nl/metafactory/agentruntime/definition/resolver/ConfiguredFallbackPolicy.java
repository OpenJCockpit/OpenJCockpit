package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.agentruntime.definition.config.AgentDefinitionProperties;
import nl.metafactory.agentruntime.definition.model.DefinitionSource;
import nl.metafactory.common.agentdefinition.SourceStatus;

/**
 * Fallback rules (remote → last-known-good → local):
 * <ul>
 *   <li>the agent's tenant binding must allow fallback ({@code fallback_allowed});</li>
 *   <li>{@code UNAVAILABLE} continues when {@code fallback.on-unavailable} is set;</li>
 *   <li>{@code NOT_FOUND} continues only when both {@code fallback.on-not-found} and the binding's
 *       {@code fallback_on_not_found} are set — except a last-known-good miss/expiry/corruption,
 *       which is part of the fallback walk itself;</li>
 *   <li>{@code UNAUTHORIZED} and {@code INVALID} never continue.</li>
 * </ul>
 * Whatever source is finally used is validated by the resolver exactly like a remote definition.
 */
public class ConfiguredFallbackPolicy implements FallbackPolicy {

    private final AgentDefinitionProperties.Fallback config;

    public ConfiguredFallbackPolicy(AgentDefinitionProperties.Fallback config) {
        this.config = config;
    }

    @Override
    public boolean mayContinueAfter(AgentTenantBinding binding, DefinitionSource failedSource, SourceStatus status) {
        if (!binding.fallbackAllowed()) {
            return false;
        }
        if (failedSource == DefinitionSource.LAST_KNOWN_GOOD
                && (status == SourceStatus.NOT_FOUND || status == SourceStatus.UNAVAILABLE)) {
            return true;
        }
        return switch (status) {
            case UNAVAILABLE -> config.isOnUnavailable();
            case NOT_FOUND -> config.isOnNotFound() && binding.fallbackOnNotFound();
            default -> false;
        };
    }
}
