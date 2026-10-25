package nl.metafactory.agentruntime.definition.resolver;

import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.agentruntime.definition.binding.AgentTenantBindings;
import nl.metafactory.agentruntime.definition.cache.LastKnownGoodStore;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.common.agentdefinition.Identifiers;
import nl.metafactory.common.agentdefinition.AgentDefinitionDocument;
import nl.metafactory.agentruntime.definition.model.AgentResolutionException;
import nl.metafactory.agentruntime.definition.model.ResolutionFailure;
import nl.metafactory.agentruntime.definition.model.ResolvedAgentDefinition;
import nl.metafactory.agentruntime.definition.runtime.PreparedAgent;
import nl.metafactory.agentruntime.definition.skill.SkillPackage;
import nl.metafactory.agentruntime.definition.skill.SkillResolver;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import nl.metafactory.agentruntime.definition.tool.ToolAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Produces the single immutable {@link PreparedAgent} for one run: tenant binding from PostgreSQL,
 * definition from the selected source, then skill, tool, model and guardrail validation. Called
 * exactly once per run before Embabel starts; nothing downstream re-resolves.
 */
public class AgentDefinitionResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentDefinitionResolver.class);

    private final AgentTenantBindings bindings;
    private final DefinitionSourceSelector selector;
    private final SkillResolver skillResolver;
    private final ToolAccessPolicy toolAccessPolicy;
    private final GuardrailPolicyEnforcer guardrails;
    private final ModelAliasCatalog models;
    private final Clock clock;
    private final LastKnownGoodStore lastKnownGood;

    public AgentDefinitionResolver(AgentTenantBindings bindings, DefinitionSourceSelector selector,
                                   SkillResolver skillResolver, ToolAccessPolicy toolAccessPolicy,
                                   GuardrailPolicyEnforcer guardrails, ModelAliasCatalog models, Clock clock) {
        this(bindings, selector, skillResolver, toolAccessPolicy, guardrails, models, clock, null);
    }

    /** @param lastKnownGood receives remote definitions that passed full resolution; may be null */
    public AgentDefinitionResolver(AgentTenantBindings bindings, DefinitionSourceSelector selector,
                                   SkillResolver skillResolver, ToolAccessPolicy toolAccessPolicy,
                                   GuardrailPolicyEnforcer guardrails, ModelAliasCatalog models, Clock clock,
                                   LastKnownGoodStore lastKnownGood) {
        this.lastKnownGood = lastKnownGood;
        this.bindings = bindings;
        this.selector = selector;
        this.skillResolver = skillResolver;
        this.toolAccessPolicy = toolAccessPolicy;
        this.guardrails = guardrails;
        this.models = models;
        this.clock = clock;
    }

    public PreparedAgent resolve(String tenantId, String agentId) {
        try {
            var prepared = doResolve(tenantId, agentId);
            var d = prepared.definition();
            log.info("agent.definition.resolved tenant={} agentId={} version={} source={} hash={} attempts={}",
                    tenantId, agentId, d.version(), d.source(), d.contentHash().substring(0, 12), d.attempts().size());
            return prepared;
        } catch (AgentResolutionException e) {
            log.warn("agent.definition.rejected tenant={} agentId={} failure={} reason={}", tenantId, agentId,
                    e.failure(), e.getMessage());
            throw e;
        }
    }

    private PreparedAgent doResolve(String tenantId, String agentId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 128) {
            throw new AgentResolutionException(ResolutionFailure.TENANT_FORBIDDEN, "no tenant");
        }
        if (!Identifiers.isSlug(agentId)) {
            throw new AgentResolutionException(ResolutionFailure.INVALID, "invalid agent id");
        }
        AgentTenantBinding binding = bindings.find(tenantId, agentId)
                .filter(AgentTenantBinding::enabled)
                .orElseThrow(() -> new AgentResolutionException(ResolutionFailure.TENANT_FORBIDDEN,
                        "agent " + agentId + " is not enabled for this tenant"));

        var selection = selector.select(binding);
        AgentDefinitionDocument doc = selection.document();
        validateAgainstBinding(doc, binding);

        var skills = skillResolver.resolve(doc);
        var tools = toolAccessPolicy.authorize(agentId, doc.tools(), binding.allowedTools());
        var requiredGuardrails = new LinkedHashSet<>(doc.guardrails());
        requiredGuardrails.addAll(binding.requiredGuardrails().stream().sorted().toList());
        var guardrailBinding = guardrails.enforce(agentId, doc.model(), new ArrayList<>(requiredGuardrails));

        var definition = new ResolvedAgentDefinition(doc.id(), tenantId, doc.instructions(), doc.model(),
                skills.skills().stream().map(SkillPackage::id).toList(),
                tools.stream().map(RegisteredAgentTool::id).toList(),
                guardrailBinding.guardrailIds(), selection.source(), doc.version(), doc.contentHash(),
                clock.instant(), guardrailBinding.executors(), selection.attempts());
        var prepared = new PreparedAgent(definition, skills.skills(), tools, guardrailBinding.guardRails(),
                doc.description());
        if (lastKnownGood != null) {
            lastKnownGood.store(doc, selection.source());
        }
        return prepared;
    }

    private void validateAgainstBinding(AgentDefinitionDocument doc, AgentTenantBinding binding) {
        if (!doc.id().equals(binding.agentId())) {
            throw new AgentResolutionException(ResolutionFailure.INVALID, "definition id does not match the requested agent");
        }
        if (binding.pinnedVersion() != null && !binding.pinnedVersion().equals(doc.version())) {
            throw new AgentResolutionException(ResolutionFailure.POLICY_DENIED,
                    "definition version " + doc.version() + " does not match pinned version " + binding.pinnedVersion());
        }
        if (binding.modelAlias() != null && !binding.modelAlias().equals(doc.model())) {
            throw new AgentResolutionException(ResolutionFailure.POLICY_DENIED, "definition model alias is not the bound alias");
        }
        if (!models.contains(doc.model())) {
            throw new AgentResolutionException(ResolutionFailure.INVALID, "model alias " + doc.model() + " is not configured");
        }
    }
}
