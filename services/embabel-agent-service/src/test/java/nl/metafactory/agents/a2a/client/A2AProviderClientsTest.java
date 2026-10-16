package nl.metafactory.agents.a2a.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2AProviderClientsTest {

    static A2AProvidersProperties props(String... types) {
        var props = new A2AProvidersProperties();
        for (String type : types) {
            var p = LiteLlmA2AProviderClientTest.config("http://litellm:4000", "sk");
            p.setType(type);
            props.getA2aProviders().add(p);
        }
        return props;
    }

    @Test
    void createsExactlyOneClientPerProvider() {
        var clients = new A2AProviderClients(props("litellm"));
        assertThat(clients.all()).singleElement().isInstanceOf(LiteLlmA2AProviderClient.class);
        assertThat(clients.find("litellm")).containsSame(clients.all().getFirst());
        assertThat(clients.litellm()).get().isSameAs(clients.all().getFirst());
        assertThat(clients.find("other")).isEmpty();
    }

    @Test
    void noProvidersMeansNoClients() {
        var clients = new A2AProviderClients(new A2AProvidersProperties());
        assertThat(clients.all()).isEmpty();
        assertThat(clients.litellm()).isEmpty();
    }

    @Test
    void unknownTypesAndDuplicatesFailStartup() {
        assertThatThrownBy(() -> new A2AProviderClients(props("unknown"))).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> new A2AProviderClients(props("litellm", "litellm"))).hasMessageContaining("twice");
    }

    @Test
    void apiKeyNeverRendered() {
        var provider = props("litellm").getA2aProviders().getFirst();
        provider.setApiKey("sk-secret-value");
        assertThat(provider.toString()).doesNotContain("sk-secret-value").contains("***");
    }

    static A2AProvidersProperties.Provider added(String id) {
        var p = LiteLlmA2AProviderClientTest.config("http://gw:4000", "sk");
        p.setType("litellm");
        p.setId(id);
        p.setName("Gateway " + id);
        return p;
    }

    @Test
    void servicesAddedInTheDashboardAreReplacedAsAWholeButNeverTakeTheLiteLlmRole() {
        var clients = new A2AProviderClients(props("litellm"));
        clients.replaceAdded(java.util.List.of(added("acme"), added("beta")));
        assertThat(clients.all()).extracting(A2AProviderClient::id).containsExactly("litellm", "acme", "beta");
        assertThat(clients.find("acme")).get().extracting(A2AProviderClient::name).isEqualTo("Gateway acme");
        assertThat(clients.isConfigured("litellm")).isTrue();
        assertThat(clients.isConfigured("acme")).isFalse();
        assertThat(clients.litellm()).get().extracting(A2AProviderClient::id).isEqualTo("litellm");

        clients.replaceAdded(java.util.List.of(added("beta")));
        assertThat(clients.all()).extracting(A2AProviderClient::id).containsExactly("litellm", "beta");

        assertThat(new A2AProviderClients(new A2AProvidersProperties()).litellm()).isEmpty();
    }

    @Test
    void anAddedServiceCannotTakeAConfiguredIdOrAppearTwice() {
        var clients = new A2AProviderClients(props("litellm"));
        assertThatThrownBy(() -> clients.replaceAdded(java.util.List.of(added("litellm")))).hasMessageContaining("twice");
        assertThatThrownBy(() -> clients.replaceAdded(java.util.List.of(added("acme"), added("acme")))).hasMessageContaining("twice");
        assertThat(clients.all()).extracting(A2AProviderClient::id).containsExactly("litellm");
    }
}
