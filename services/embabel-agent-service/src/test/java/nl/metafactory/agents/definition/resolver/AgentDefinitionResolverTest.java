package nl.metafactory.agents.definition.resolver;

import nl.metafactory.agents.definition.binding.AgentTenantBinding;
import nl.metafactory.agents.definition.model.AgentResolutionException;
import nl.metafactory.agents.definition.model.DefinitionSource;
import nl.metafactory.agents.definition.model.ResolutionFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Set;

import static nl.metafactory.agents.definition.resolver.ResolverFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDefinitionResolverTest {

    static void assertFailure(Executable call, ResolutionFailure expected) {
        assertThatThrownBy(call::execute).isInstanceOf(AgentResolutionException.class)
                .extracting(e -> ((AgentResolutionException) e).failure()).isEqualTo(expected);
    }

    @Test
    void resolvesTheConcreteLocalConfiguration() {
        var resolver = localResolver(repo(binding("tenant-a", Set.of("customer.read"), Set.of())));

        var prepared = resolver.resolve("tenant-a", "customer-support");
        var d = prepared.definition();

        assertThat(d.agentId()).isEqualTo("customer-support");
        assertThat(d.tenantId()).isEqualTo("tenant-a");
        assertThat(d.modelAlias()).isEqualTo("customer-chat");
        assertThat(d.skillIds()).containsExactly("customer-lookup");
        assertThat(d.toolIds()).containsExactly("customer.read");
        assertThat(d.guardrailIds()).containsExactly("pii-protection");
        assertThat(d.guardrailExecutors()).containsKey("pii-protection");
        assertThat(d.source()).isEqualTo(DefinitionSource.LOCAL);
        assertThat(d.resolvedAt()).isEqualTo(CLOCK.instant());
        assertThat(d.instructions()).contains("hun account");
        assertThat(prepared.skills()).hasSize(1);
        assertThat(prepared.guardRails()).hasSize(2);
    }

    @Test
    void tenantWithoutBindingIsForbidden() {
        var resolver = localResolver(repo(binding("tenant-a", Set.of("customer.read"), Set.of())));
        assertFailure(() -> resolver.resolve("tenant-b", "customer-support"), ResolutionFailure.TENANT_FORBIDDEN);
        assertFailure(() -> resolver.resolve("", "customer-support"), ResolutionFailure.TENANT_FORBIDDEN);
    }

    @Test
    void disabledBindingIsForbidden() {
        var disabled = new AgentTenantBinding("tenant-a", "customer-support", false, null, null, false, false,
                Set.of("customer.read"), Set.of());
        assertFailure(() -> localResolver(repo(disabled)).resolve("tenant-a", "customer-support"),
                ResolutionFailure.TENANT_FORBIDDEN);
    }

    @Test
    void toolNotGrantedToTenantIsDenied() {
        var resolver = localResolver(repo(binding("tenant-a", Set.of(), Set.of())));
        assertFailure(() -> resolver.resolve("tenant-a", "customer-support"), ResolutionFailure.TOOL_DENIED);
    }

    @Test
    void pinnedVersionMismatchIsDenied() {
        var pinned = new AgentTenantBinding("tenant-a", "customer-support", true, null, "2.0.0", false, false,
                Set.of("customer.read"), Set.of());
        assertFailure(() -> localResolver(repo(pinned)).resolve("tenant-a", "customer-support"),
                ResolutionFailure.POLICY_DENIED);
    }

    @Test
    void boundModelAliasMismatchIsDenied() {
        var pinned = new AgentTenantBinding("tenant-a", "customer-support", true, "best", null, false, false,
                Set.of("customer.read"), Set.of());
        assertFailure(() -> localResolver(repo(pinned)).resolve("tenant-a", "customer-support"),
                ResolutionFailure.POLICY_DENIED);
    }

    @Test
    void unconfiguredModelAliasIsInvalid() {
        var resolver = resolver(repo(binding("tenant-a", Set.of("customer.read"), Set.of())),
                List.of(localProvider()), FallbackPolicy.NONE, Set.of(), Set.of("best"));
        assertFailure(() -> resolver.resolve("tenant-a", "customer-support"), ResolutionFailure.INVALID);
    }

    @Test
    void bindingRequiredGuardrailWithoutExecutorFailsClosed() {
        var resolver = localResolver(repo(binding("tenant-a", Set.of("customer.read"), Set.of("toxicity"))));
        assertFailure(() -> resolver.resolve("tenant-a", "customer-support"), ResolutionFailure.GUARDRAIL_MISSING);
    }

    @Test
    void unknownAgentIsNotFoundAndTraversalIdIsInvalid() {
        var other = new AgentTenantBinding("tenant-a", "billing", true, null, null, false, false, Set.of(), Set.of());
        assertFailure(() -> localResolver(repo(other)).resolve("tenant-a", "billing"), ResolutionFailure.NOT_FOUND);
        assertFailure(() -> localResolver(repo()).resolve("tenant-a", "../customer-support"), ResolutionFailure.INVALID);
    }
}
