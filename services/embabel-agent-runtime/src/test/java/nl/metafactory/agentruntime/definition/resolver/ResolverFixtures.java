package nl.metafactory.agentruntime.definition.resolver;

import com.embabel.agent.api.tool.Tool;
import nl.metafactory.common.agentruntime.AgentTenantBinding;
import nl.metafactory.agentruntime.definition.binding.AgentTenantBindings;
import nl.metafactory.agentruntime.definition.guardrail.GuardrailPolicyEnforcer;
import nl.metafactory.agentruntime.definition.guardrail.PiiProtectionGuardrail;
import nl.metafactory.common.agentdefinition.AgentMarkdownParser;
import nl.metafactory.agentruntime.definition.skill.LocalSkillInventory;
import nl.metafactory.agentruntime.definition.skill.SkillMarkdownParser;
import nl.metafactory.agentruntime.definition.skill.SkillResolver;
import nl.metafactory.agentruntime.definition.source.AgentDefinitionProvider;
import nl.metafactory.common.agentdefinition.ConfinedRoot;
import nl.metafactory.agentruntime.definition.source.LocalMarkdownAgentDefinitionProvider;
import nl.metafactory.agentruntime.definition.tool.RegisteredAgentTool;
import nl.metafactory.agentruntime.definition.tool.ToolAccessPolicy;
import nl.metafactory.agentruntime.definition.tool.ToolRegistry;

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

    static AgentTenantBindings repo(AgentTenantBinding... bindings) {
        var repo = mock(AgentTenantBindings.class);
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

    static AgentDefinitionResolver resolver(AgentTenantBindings repo, List<AgentDefinitionProvider> providers,
                                            FallbackPolicy fallback, Set<String> mandatory, Set<String> models) {
        return new AgentDefinitionResolver(repo, new SourceChainSelector(providers, fallback),
                new SkillResolver(List.of(new LocalSkillInventory(new ConfinedRoot(ROOT), new SkillMarkdownParser()))),
                new ToolAccessPolicy(tools()),
                new GuardrailPolicyEnforcer(List.of(new PiiProtectionGuardrail()), mandatory),
                new ModelAliasCatalog(models), CLOCK);
    }

    static AgentDefinitionResolver localResolver(AgentTenantBindings repo) {
        return resolver(repo, List.of(localProvider()), FallbackPolicy.NONE, Set.of(), Set.of("customer-chat"));
    }

    static Map<String, String> none() {
        return Map.of();
    }
}
