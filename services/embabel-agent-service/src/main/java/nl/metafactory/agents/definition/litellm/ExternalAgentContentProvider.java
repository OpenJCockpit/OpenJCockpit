package nl.metafactory.agents.definition.litellm;

import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ProviderResult;
import nl.metafactory.agents.definition.model.SourceStatus;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;

/**
 * Remote agent-definition content from LiteLLM — {@code UNSUPPORTED} for the pinned LiteLLM
 * v1.101.2. Verified in that tag's source: the A2A Agent Registry ({@code GET /v1/agents[/{id}]},
 * {@code AgentResponse}) stores only {@code agent_card_params} (an A2A Agent Card),
 * {@code litellm_params} (routing parameters, secret-redacted) and limits; there is no field or
 * route that holds Markdown instructions, guardrail bindings or tool grants. Reusing the Agent Card
 * or {@code litellm_params} as a prompt would invent a contract, so this provider always reports
 * {@code UNSUPPORTED} and the resolver moves on to the next source.
 *
 * <p>Replace this class only when a LiteLLM version with a documented, verifiable content API is
 * pinned in {@code docker-compose.yml}.
 */
public class ExternalAgentContentProvider implements AgentDefinitionProvider {

    public static final String REASON =
            "LiteLLM v1.101.2 exposes no agent-definition content API (Agent Card metadata only)";

    @Override
    public DefinitionSource source() {
        return DefinitionSource.REMOTE_LITELLM;
    }

    @Override
    public ProviderResult lookup(String agentId) {
        return ProviderResult.of(SourceStatus.UNSUPPORTED, REASON);
    }
}
