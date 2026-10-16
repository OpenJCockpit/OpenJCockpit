package nl.metafactory.agents.a2a.services;

import nl.metafactory.agents.a2a.client.A2AProviderClients;
import nl.metafactory.agents.a2a.client.A2AProvidersProperties;
import nl.metafactory.agents.workflow.agents.JdbcWorkflowAgentLinks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A2A services added in the dashboard: V6 table, encrypted secrets, live client registry (H2). */
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DataJpaTest
class A2AServicesTest {

    static final String SECRET = "test-a2a-services-secret";
    static final String SALT = "5c0744940b5c369b";
    // nothing listens here: listing agents fails fast with UNAVAILABLE
    static final String GATEWAY = "http://127.0.0.1:1";

    @Autowired JdbcClient jdbc;

    A2AServiceStore store;
    A2AProvidersProperties configured;
    A2AProviderClients clients;
    A2AServices services;

    @BeforeEach
    void setUp() {
        store = new A2AServiceStore(jdbc);
        configured = new A2AProvidersProperties();
        var litellm = new A2AProvidersProperties.Provider();
        litellm.setType("litellm");
        litellm.setName("LiteLLM");
        litellm.setBaseUrl(GATEWAY);
        litellm.setApiKey("sk-configured");
        configured.getA2aProviders().add(litellm);
        clients = new A2AProviderClients(configured);
        services = services(new A2AServiceSecrets(SECRET, SALT));
    }

    A2AServices services(A2AServiceSecrets secrets) {
        return new A2AServices(store, secrets, clients, configured);
    }

    static A2AServiceRequest request(String id, String apiKey) {
        return new A2AServiceRequest(id, "Acme gateway", GATEWAY + "/", "https://a2a.acme.example", apiKey,
                null, null, null);
    }

    static A2AServiceRequest withCaller(A2AServiceRequest r, String tokenUrl, String clientId, String secret) {
        return new A2AServiceRequest(r.id(), r.name(), r.baseUrl(), r.publicUrl(), r.apiKey(), tokenUrl, clientId, secret);
    }

    static HttpStatus status(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    @Test
    void anAddedServiceIsStoredWithEncryptedSecretsAndUsableAtOnce() {
        var created = services.create(withCaller(request("acme", "sk-acme-secret"),
                "https://idp.acme.example/token", "openjcockpit", "client-secret-value"), "user-sub");

        assertThat(created.id()).isEqualTo("acme");
        assertThat(created.source()).isEqualTo("DASHBOARD");
        assertThat(created.editable()).isTrue();
        assertThat(created.baseUrl()).isEqualTo(GATEWAY);
        assertThat(created.hasApiKey()).isTrue();
        assertThat(created.hasCallerClientSecret()).isTrue();
        assertThat(created.status()).isEqualTo("UNAVAILABLE");
        assertThat(created.toString()).doesNotContain("sk-acme-secret").doesNotContain("client-secret-value");

        String stored = jdbc.sql("SELECT api_key_encrypted || caller_client_secret_encrypted FROM a2a_services WHERE id = 'acme'")
                .query(String.class).single();
        assertThat(stored).doesNotContain("sk-acme-secret").doesNotContain("client-secret-value");
        assertThat(jdbc.sql("SELECT created_by FROM a2a_services").query(String.class).single()).isEqualTo("user-sub");

        var client = clients.find("acme").orElseThrow();
        assertThat(client.name()).isEqualTo("Acme gateway");
        assertThat(client.configured()).isTrue();
        assertThat(clients.litellm()).get().extracting(c -> c.id()).isEqualTo("litellm");
    }

    @Test
    void configuredServicesAreListedReadOnlyAndCannotBeChanged() {
        services.create(request("acme", "sk-acme"), "user-sub");

        var list = services.list();
        assertThat(list).extracting(A2AServiceDto::id).containsExactly("litellm", "acme");
        var litellm = list.getFirst();
        assertThat(litellm.source()).isEqualTo("CONFIGURATION");
        assertThat(litellm.editable()).isFalse();
        assertThat(litellm.hasApiKey()).isTrue();
        assertThat(litellm.status()).isEqualTo("UNAVAILABLE");

        assertThatThrownBy(() -> services.update("litellm", request(null, "x"), "u"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> services.delete("litellm"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> services.create(request("litellm", "x"), "u"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> services.create(request("acme", "x"), "u"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void invalidInputIsRejected() {
        for (var bad : List.of(
                request("Acme", "k"), request("a", "k"), request("local", "k"), request("acme--gw", "k"),
                request("acme", ""),
                new A2AServiceRequest("acme", " ", GATEWAY, null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", null, null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", "ftp://gw.example", null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", "https://user:pw@gw.example", null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", "https://gw.example?key=1", null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", "gw.example", null, "k", null, null, null),
                new A2AServiceRequest("acme", "Acme", GATEWAY, "javascript:alert(1)", "k", null, null, null),
                withCaller(request("acme", "k"), "https://idp.example/token", null, "s"),
                withCaller(request("acme", "k"), null, "client", "s"),
                withCaller(request("acme", "k"), null, null, "s"),
                withCaller(request("acme", "k"), "https://idp.example/token", "client", null))) {
            assertThatThrownBy(() -> services.create(bad, "u")).as(bad.toString())
                    .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(store.list()).isEmpty();
        assertThat(clients.find("acme")).isEmpty();
    }

    @Test
    void anUpdateKeepsBlankSecretsAndCanRemoveTheCallerIdentity() {
        services.create(withCaller(request("acme", "sk-1"), "https://idp.example/token", "client", "secret-1"), "creator");
        String key = jdbc.sql("SELECT api_key_encrypted FROM a2a_services").query(String.class).single();

        var updated = services.update("acme", withCaller(new A2AServiceRequest(null, "Acme renamed", "https://gw2.example",
                null, "", null, null, null), "https://idp.example/token", "client", ""), "editor");
        assertThat(updated.name()).isEqualTo("Acme renamed");
        assertThat(updated.hasCallerClientSecret()).isTrue();
        assertThat(jdbc.sql("SELECT api_key_encrypted FROM a2a_services").query(String.class).single()).isEqualTo(key);
        assertThat(jdbc.sql("SELECT created_by || '/' || updated_by FROM a2a_services").query(String.class).single())
                .isEqualTo("creator/editor");
        assertThat(clients.find("acme").orElseThrow().name()).isEqualTo("Acme renamed");

        var withoutCaller = services.update("acme", request(null, null), "editor");
        assertThat(withoutCaller.callerTokenUrl()).isNull();
        assertThat(withoutCaller.hasCallerClientSecret()).isFalse();

        assertThatThrownBy(() -> services.update("acme", request("other", null), "editor"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> services.update("missing", request(null, "k"), "editor"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aServiceUsedByAWorkflowCannotBeDeleted() {
        services.create(request("acme", "sk"), "u");
        new JdbcWorkflowAgentLinks(jdbc).replace("wf-1", List.of("a2a:acme:reviewer"));

        assertThatThrownBy(() -> services.delete("acme"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("wf-1");
        assertThat(clients.find("acme")).isPresent();

        new JdbcWorkflowAgentLinks(jdbc).delete("wf-1");
        services.delete("acme");
        assertThat(store.find("acme")).isEmpty();
        assertThat(clients.find("acme")).isEmpty();
    }

    @Test
    void withoutAnEncryptionKeyNothingIsStoredAndStoredServicesStayUnusable() {
        var noKey = services(new A2AServiceSecrets("", ""));
        assertThatThrownBy(() -> noKey.create(request("acme", "sk"), "u"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(store.list()).isEmpty();

        services.create(request("acme", "sk"), "u");
        noKey.load();
        assertThat(clients.find("acme").orElseThrow().configured()).isFalse();
        var listed = noKey.list().getLast();
        assertThat(listed.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(listed.message()).contains("encryption is not configured");
    }

    @Test
    void secretsEncryptedWithAnotherKeyMakeTheServiceUnusable() {
        services.create(withCaller(request("acme", "sk"), "https://idp.example/token", "client", "s"), "u");
        var otherKey = services(new A2AServiceSecrets("another-secret-of-enough-length", SALT));
        otherKey.load();
        var client = clients.find("acme").orElseThrow();
        assertThat(client.configured()).isFalse();
        assertThat(client.callerAuthorization()).isEmpty();
        assertThat(otherKey.list().getLast().message()).contains("cannot be decrypted");
    }

    @Test
    void storedServicesAreRegisteredAtStartup() {
        services.create(request("acme", "sk"), "u");
        var restartedClients = new A2AProviderClients(configured);
        new A2AServices(store, new A2AServiceSecrets(SECRET, SALT), restartedClients, configured).load();
        assertThat(restartedClients.find("acme")).get().satisfies(c -> assertThat(c.configured()).isTrue());
    }
}
