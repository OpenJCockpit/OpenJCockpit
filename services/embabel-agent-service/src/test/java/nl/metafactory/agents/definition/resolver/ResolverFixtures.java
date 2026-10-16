package nl.metafactory.agents.definition.resolver;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.binding.AgentTenantBindingRepository;
import nl.metafactory.agents.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agents.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.agents.definition.markdown.AgentMarkdownParser;
import nl.metafactory.agents.definition.skill.LocalSkillInventory;
import nl.metafactory.agents.definition.skill.SkillMarkdownParser;
import nl.metafactory.agents.definition.skill.SkillResolver;
import nl.metafactory.agents.definition.source.AgentDefinitionProvider;
import nl.metafactory.agents.definition.source.ConfinedRoot;
import nl.metafactory.agents.definition.source.LocalMarkdownAgentDefinitionProvider;
import nl.metafactory.agents.definition.tool.RegisteredAgentTool;
import nl.metafactory.agents.definition.tool.ToolAccessPolicy;
import nl.metafactory.agents.definition.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared wiring for resolver tests: real local fixtures, in-memory bindings. */
final class ResolverFixtures {

    static final Path ROOT = Path.of("src/test/resources/agent-definitions");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);

    private ResolverFixtures() {
    }

    static AgentTenantBinding binding(String tenant, Set<String> tools, Set<String> guardrails) {
        return new AgentTenantBinding(tenant, "customer-support", true, null, null, false, false, tools, guardrails);
    }

    static AgentTenantBindingRepository repo(AgentTenantBinding... bindings) {
        var repo = mock(AgentTenantBindingRepository.class);
        when(repo.find(anyString(), anyString())).thenReturn(Optional.empty());
        for (AgentTenantBinding b : bindings) {
            when(repo.find(b.tenantId(), b.agentId())).thenReturn(Optional.of(b));
        }
        return repo;
    }

    static LocalMarkdownAgentDefinitionProvider localProvider() {
        return new LocalMarkdownAgentDefinitionProvider(new ConfinedRoot(ROOT), new AgentMarkdownParser());
    }

    static ToolRegistry tools() {
        return new ToolRegistry(List.of(
                new RegisteredAgentTool("customer.read", Tool.create("customer_read", "read", in -> Tool.Result.text("ok"))),
                new RegisteredAgentTool("customer.delete", Tool.create("customer_delete", "delete", in -> Tool.Result.text("ok")))));
    }

    static AgentDefinitionResolver resolver(AgentTenantBindingRepository repo, List<AgentDefinitionProvider> providers,
                                            FallbackPolicy fallback, Set<String> mandatory, Set<String> models) {
        return new AgentDefinitionResolver(repo, new SourceChainSelector(providers, fallback),
                new SkillResolver(List.of(new LocalSkillInventory(new ConfinedRoot(ROOT), new SkillMarkdownParser()))),
                new ToolAccessPolicy(tools()),
                new GuardrailPolicyEnforcer(List.of(new PiiProtectionGuardrail()), mandatory),
                new ModelAliasCatalog(models), CLOCK);
    }

    static AgentDefinitionResolver localResolver(AgentTenantBindingRepository repo) {
        return resolver(repo, List.of(localProvider()), FallbackPolicy.NONE, Set.of(), Set.of("customer-chat"));
    }

    static Map<String, String> none() {
        return Map.of();
    }
}
