package nl.metafactory.agentruntime.definition.runtime;

import nl.metafactory.agentruntime.definition.binding.AgentTenantBindings;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agentruntime.definition.resolver.AgentDefinitionResolver;
import nl.metafactory.agentruntime.definition.resolver.FallbackPolicy;
import nl.metafactory.agentruntime.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agentruntime.definition.resolver.SourceChainSelector;
import nl.metafactory.agentruntime.definition.skill.SkillPackageProvider;
import nl.metafactory.agentruntime.definition.skill.SkillResolver;
import nl.metafactory.agentruntime.definition.source.SnapshotAgentDefinitionProvider;
import nl.metafactory.agentruntime.definition.tool.ToolAccessPolicy;
import nl.metafactory.agentruntime.definition.tool.ToolRegistry;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.common.agentruntime.AgentExecutionRequest.DefinitionSnapshot;

import java.time.Clock;
import java.util.List;

/**
 * Runs agents of the agent service's catalog ({@code local:} references of workflows) from the definition
 * snapshot sent with the request. Same validation as served agents — tenant binding, skills, tool
 * allowlist, guardrails, model alias — but no fallback: the catalog is the only source.
 */
public class CatalogAgentRuns {

    private final AgentTenantBindings bindings;
    private final AgentMarkdownParser parser;
    private final SkillPackageProvider skills;
    private final ToolRegistry tools;
    private final GuardrailPolicyEnforcer guardrails;
    private final ModelAliasCatalog models;
    private final ConfiguredDefinitionAgent agent;

    public CatalogAgentRuns(AgentTenantBindings bindings, AgentMarkdownParser parser, SkillPackageProvider skills,
                            ToolRegistry tools, GuardrailPolicyEnforcer guardrails, ModelAliasCatalog models,
                            ConfiguredDefinitionAgent agent) {
        this.bindings = bindings;
        this.parser = parser;
        this.skills = skills;
        this.tools = tools;
        this.guardrails = guardrails;
        this.models = models;
        this.agent = agent;
    }

    public AgentRunService forSnapshot(DefinitionSnapshot snapshot) {
        var selector = new SourceChainSelector(List.of(new SnapshotAgentDefinitionProvider(snapshot, parser)),
                FallbackPolicy.NONE);
        var resolver = new AgentDefinitionResolver(bindings, selector, new SkillResolver(List.of(skills)),
                new ToolAccessPolicy(tools), guardrails, models, Clock.systemUTC());
        return new AgentRunService(resolver, agent);
    }
}
