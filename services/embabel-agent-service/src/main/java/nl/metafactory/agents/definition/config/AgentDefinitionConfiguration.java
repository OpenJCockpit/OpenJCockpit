package nl.metafactory.agents.definition.config;

import nl.metafactory.agents.a2a.client.A2AProviderClients;
import nl.metafactory.agents.a2a.client.LiteLlmA2AProviderClient;
import nl.metafactory.agents.catalog.AgentCatalogProperties;
import nl.metafactory.agents.catalog.LocalAgentCatalog;
import nl.metafactory.agents.config.AgentGatewayModelRegistrar;
import nl.metafactory.agents.workflow.agents.CatalogLocalAgentDefinitionProvider;
import nl.metafactory.agents.workflow.agents.LocalCatalogAgentRunner;
import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.agents.definition.cache.LastKnownGoodStore;
import nl.metafactory.agents.definition.config.AgentDefinitionProperties.SourceKind;
import nl.metafactory.agents.definition.guardrail.GuardrailExecutor;
import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.litellm.ExternalAgentContentProvider;
import nl.metafactory.agents.definition.litellm.ExternalSkillPackageProvider;
import nl.metafactory.agents.definition.litellm.LiteLlmPolicyGuardrailExecutor;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.resolver.AgentDefinitionResolver;
import nl.metafactory.agents.definition.resolver.ConfiguredFallbackPolicy;
import nl.metafactory.agents.definition.resolver.ModelAliasCatalog;
import nl.metafactory.agents.definition.resolver.SourceChainSelector;
import nl.metafactory.agents.definition.resolver.SourceOrder;
import nl.metafactory.agents.definition.runtime.AgentRunService;
import nl.metafactory.agents.definition.runtime.ConfiguredDefinitionAgent;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillPackageProvider;
import nl.metafactory.agents.definition.skill.SkillResolver;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.source.LocalMarkdownAgentDefinitionProvider;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import nl.metafactory.agents.definition.tool.ToolAccessPolicy;
import nl.metafactory.agents.definition.tool.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Wiring for SPEC-002. Nothing here is created unless {@code openjcockpit.agent-definitions.enabled=true}. */
@Configuration
@ConditionalOnProperty(name = "openjcockpit.agent-definitions.enabled", havingValue = "true")
public class AgentDefinitionConfiguration {

    @Bean
    static AgentGatewayModelRegistrar agentGatewayModelRegistrar(Environment environment) {
        return new AgentGatewayModelRegistrar(environment);
    }

    @Bean
    ConfinedRoot agentDefinitionRoot(AgentDefinitionProperties properties) {
        return new ConfinedRoot(Path.of(properties.getLocalRoot()));
    }

    @Bean
    LocalMarkdownAgentDefinitionProvider localAgentDefinitionProvider(ConfinedRoot root, AgentMarkdownParser parser) {
        return new LocalMarkdownAgentDefinitionProvider(root, parser);
    }

    @Bean
    LocalSkillInventory localSkillInventory(ConfinedRoot root, SkillMarkdownParser parser) {
        return new LocalSkillInventory(root, parser);
    }

    @Bean
    LastKnownGoodStore lastKnownGoodStore(AgentDefinitionProperties properties, AgentMarkdownParser parser) {
        var cache = properties.getCache();
        return cache.getDirectory() == null || cache.getDirectory().isBlank() ? null
                : new LastKnownGoodStore(Path.of(cache.getDirectory()), cache.getTtl(), cache.getMaxEntries(),
                Clock.systemUTC(), parser);
    }

    @Bean
    ToolRegistry agentToolRegistry(ObjectProvider<RegisteredAgentTool> tools) {
        return new ToolRegistry(tools.orderedStream().toList());
    }

    @Bean
    GuardrailPolicyEnforcer guardrailPolicyEnforcer(AgentDefinitionProperties properties, List<GuardrailExecutor> local,
                                                    A2AProviderClients providers) {
        var executors = new ArrayList<>(local);
        var client = providers.litellm().flatMap(LiteLlmA2AProviderClient::http).orElse(null);
        properties.getLitellm().getGatewayGuardrails().forEach((id, litellmName) -> {
            if (client == null) {
                throw new IllegalStateException("gateway guardrail " + id + " requires a configured LiteLLM A2A provider");
            }
            executors.add(new LiteLlmPolicyGuardrailExecutor(id, litellmName, client, Clock.systemUTC()));
        });
        return new GuardrailPolicyEnforcer(executors, new HashSet<>(properties.getMandatoryGuardrails()));
    }

    @Bean
    ModelAliasCatalog modelAliasCatalog(Environment environment) {
        Map<String, String> roles = Binder.get(environment)
                .bind("embabel.models.llms", Bindable.mapOf(String.class, String.class)).orElse(Map.of());
        return new ModelAliasCatalog(roles.keySet());
    }

    @Bean
    AgentTenantBindingRepository agentTenantBindingRepository(JdbcClient jdbc) {
        return new AgentTenantBindingRepository(jdbc);
    }

    @Bean
    AgentDefinitionResolver agentDefinitionResolver(AgentDefinitionProperties properties,
                                                    AgentTenantBindingRepository bindings,
                                                    LocalMarkdownAgentDefinitionProvider local,
                                                    LocalSkillInventory localSkills,
                                                    A2AProviderClients providers,
                                                    ObjectProvider<LastKnownGoodStore> lkg,
                                                    ToolRegistry tools, GuardrailPolicyEnforcer guardrails,
                                                    ModelAliasCatalog models) {
        var available = new EnumMap<SourceKind, AgentDefinitionProvider>(SourceKind.class);
        var skillProviders = new ArrayList<SkillPackageProvider>();
        if (providers.litellm().isPresent()) {
            available.put(SourceKind.REMOTE, new ExternalAgentContentProvider());
            skillProviders.add(new ExternalSkillPackageProvider());
        }
        var store = lkg.getIfAvailable();
        if (store != null) {
            available.put(SourceKind.LAST_KNOWN_GOOD, store);
        }
        available.put(SourceKind.LOCAL, local);
        skillProviders.add(localSkills);
        var selector = new SourceChainSelector(SourceOrder.order(available, properties.getSourcePriority()),
                new ConfiguredFallbackPolicy(properties.getFallback()));
        return new AgentDefinitionResolver(bindings, selector, new SkillResolver(skillProviders),
                new ToolAccessPolicy(tools), guardrails, models, Clock.systemUTC(), store);
    }

    @Bean
    AgentRunService agentRunService(AgentDefinitionResolver resolver, ConfiguredDefinitionAgent agent) {
        return new AgentRunService(resolver, agent);
    }

    /**
     * In-process runner for workflow agents of the repository {@code /agents} folder (catalog Local
     * section). Same validation as served agents: tenant binding, skills, tool allowlist, guardrails,
     * model alias. No fallback: the catalog folder is the only source.
     */
    @Bean
    LocalCatalogAgentRunner localCatalogAgentRunner(LocalAgentCatalog catalog, AgentCatalogProperties catalogProperties,
                                                    AgentMarkdownParser parser, AgentTenantBindingRepository bindings,
                                                    LocalSkillInventory localSkills, ToolRegistry tools,
                                                    GuardrailPolicyEnforcer guardrails, ModelAliasCatalog models,
                                                    ConfiguredDefinitionAgent agent) {
        var selector = new SourceChainSelector(
                List.of(new CatalogLocalAgentDefinitionProvider(catalog, catalogProperties, parser)),
                nl.metafactory.agents.definition.resolver.FallbackPolicy.NONE);
        var resolver = new AgentDefinitionResolver(bindings, selector, new SkillResolver(List.of(localSkills)),
                new ToolAccessPolicy(tools), guardrails, models, Clock.systemUTC());
        return new LocalCatalogAgentRunner(new AgentRunService(resolver, agent));
    }
}
