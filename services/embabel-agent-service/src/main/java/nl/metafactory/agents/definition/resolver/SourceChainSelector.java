package nl.metafactory.agents.definition.resolver;

import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.ProviderResult;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import nl.metafactory.agents.definition.model.SourceAttempt;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the configured providers in priority order. A provider reporting {@code UNSUPPORTED} (the
 * source does not exist for this deployment) is skipped; the first {@code FOUND} wins; any other
 * status ends the walk unless {@link FallbackPolicy} explicitly permits continuing.
 */
public class SourceChainSelector implements DefinitionSourceSelector {

    private final List<AgentDefinitionProvider> providers;
    private final FallbackPolicy fallbackPolicy;

    public SourceChainSelector(List<AgentDefinitionProvider> providers, FallbackPolicy fallbackPolicy) {
        this.providers = List.copyOf(providers);
        this.fallbackPolicy = fallbackPolicy;
    }

    @Override
    public DefinitionSelection select(AgentTenantBinding binding) {
        var attempts = new ArrayList<SourceAttempt>();
        ProviderResult last = null;
        for (AgentDefinitionProvider provider : providers) {
            last = provider.lookup(binding.agentId());
            attempts.add(new SourceAttempt(provider.source(), last.status(), last.reason()));
            if (last.status() == SourceStatus.FOUND) {
                return new DefinitionSelection(last.document(), provider.source(), attempts);
            }
            if (last.status() == SourceStatus.UNSUPPORTED) {
                continue;
            }
            if (!fallbackPolicy.mayContinueAfter(binding, provider.source(), last.status())) {
                throw failure(binding, last, attempts);
            }
        }
        throw failure(binding, last, attempts);
    }

    private static AgentResolutionException failure(AgentTenantBinding binding, ProviderResult last,
                                                    List<SourceAttempt> attempts) {
        var status = last == null ? SourceStatus.NOT_FOUND : last.status();
        var failure = switch (status) {
            case UNAUTHORIZED -> ResolutionFailure.UNAUTHORIZED;
            case INVALID -> ResolutionFailure.INVALID;
            case UNAVAILABLE -> ResolutionFailure.UNAVAILABLE;
            case UNSUPPORTED -> ResolutionFailure.UNSUPPORTED;
            default -> ResolutionFailure.NOT_FOUND;
        };
        var walk = attempts.stream().map(a -> a.source() + "=" + a.status()).toList();
        return new AgentResolutionException(failure, "no usable definition for agent " + binding.agentId() + " " + walk);
    }
}
