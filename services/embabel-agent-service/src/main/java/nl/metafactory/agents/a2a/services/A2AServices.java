package nl.metafactory.agents.a2a.services;

import nl.metafactory.agents.a2a.client.A2AProviderClient;
import nl.metafactory.agents.a2a.client.A2AProviderClients;
import nl.metafactory.agents.a2a.client.A2AProvidersProperties;
import nl.metafactory.agents.a2a.client.LiteLlmA2AProviderClient;
import nl.metafactory.agents.definition.markdown.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The A2A services the dashboard manages (Workflow designer → Configuration → A2A services): the
 * providers from configuration (read-only) plus LiteLLM-compatible gateways added in the dashboard,
 * stored in {@code a2a_services} with encrypted secrets. Every change replaces the dashboard-added
 * clients in {@link A2AProviderClients}, so the catalog and workflow runs use it immediately.
 * An added service whose secrets cannot be decrypted stays listed but unusable (fail closed).
 * Logs carry only the service id and action — no secrets, URLs with credentials or user identities.
 */
@Service
public class A2AServices {

    private static final Logger log = LoggerFactory.getLogger(A2AServices.class);
    static final int MAX_ID_LENGTH = 40;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_URL_LENGTH = 2048;
    static final int MAX_SECRET_LENGTH = 4096;

    private final A2AServiceStore store;
    private final A2AServiceSecrets secrets;
    private final A2AProviderClients clients;
    private final A2AProvidersProperties configured;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public A2AServices(A2AServiceStore store, A2AServiceSecrets secrets, A2AProviderClients clients,
                       A2AProvidersProperties configured) {
        this(store, secrets, clients, configured, Clock.systemUTC());
    }

    A2AServices(A2AServiceStore store, A2AServiceSecrets secrets, A2AProviderClients clients,
                A2AProvidersProperties configured, Clock clock) {
        this.store = store;
        this.secrets = secrets;
        this.clients = clients;
        this.configured = configured;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.warn("a2a.services.load failed: {}", e.getClass().getSimpleName());
        }
    }

    public List<A2AServiceDto> list() {
        var result = new ArrayList<A2AServiceDto>();
        for (var provider : configured.getA2aProviders()) {
            var auth = provider.getCallerAuth();
            result.add(withStatus(provider.effectiveId(), new A2AServiceDto(provider.effectiveId(),
                    clients.find(provider.effectiveId()).map(A2AProviderClient::name).orElse(provider.getName()),
                    provider.getType(), "CONFIGURATION", false, provider.getBaseUrl(), provider.effectivePublicUrl(),
                    notBlank(provider.getApiKey()), blankToNull(auth.getTokenUrl()), blankToNull(auth.getClientId()),
                    notBlank(auth.getClientSecret()), null, null, null, null)));
        }
        for (var s : store.list()) {
            result.add(withStatus(s.id(), toDto(s)));
        }
        return result;
    }

    public synchronized A2AServiceDto create(A2AServiceRequest request, String actor) {
        String id = request.id() == null ? "" : request.id().trim();
        if (!Identifiers.isSlug(id) || id.length() < 2 || id.length() > MAX_ID_LENGTH || "local".equals(id)) {
            throw badRequest("Id must be 2-" + MAX_ID_LENGTH + " lowercase letters, digits and single hyphens (not 'local')");
        }
        if (clients.isConfigured(id) || store.find(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An A2A service with id '" + id + "' already exists");
        }
        if (!notBlank(request.apiKey())) {
            throw badRequest("API key is required");
        }
        var validated = validate(request, null);
        Instant now = clock.instant();
        store.insert(new A2AServiceStore.StoredService(id, LiteLlmA2AProviderClient.TYPE, validated.name(),
                validated.baseUrl(), validated.publicUrl(), validated.apiKeyEncrypted(), validated.tokenUrl(),
                validated.clientId(), validated.clientSecretEncrypted(), actor, now, actor, now));
        reload();
        log.info("a2a.service.created id={}", id);
        return withStatus(id, toDto(store.find(id).orElseThrow()));
    }

    public synchronized A2AServiceDto update(String id, A2AServiceRequest request, String actor) {
        rejectConfigured(id);
        var existing = store.find(id).orElseThrow(() -> notFound(id));
        if (request.id() != null && !request.id().isBlank() && !request.id().trim().equals(id)) {
            throw badRequest("The id of an A2A service cannot be changed");
        }
        var validated = validate(request, existing);
        store.update(new A2AServiceStore.StoredService(id, existing.type(), validated.name(), validated.baseUrl(),
                validated.publicUrl(), validated.apiKeyEncrypted(), validated.tokenUrl(), validated.clientId(),
                validated.clientSecretEncrypted(), existing.createdBy(), existing.createdAt(), actor, clock.instant()));
        reload();
        log.info("a2a.service.updated id={}", id);
        return withStatus(id, toDto(store.find(id).orElseThrow()));
    }

    public synchronized void delete(String id) {
        rejectConfigured(id);
        store.find(id).orElseThrow(() -> notFound(id));
        var workflows = store.workflowsUsing(id);
        if (!workflows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A2A service '" + id + "' is used by workflow"
                    + (workflows.size() == 1 ? " " : "s ") + String.join(", ", workflows)
                    + ". Remove its agents from " + (workflows.size() == 1 ? "that workflow" : "those workflows") + " first.");
        }
        store.delete(id);
        reload();
        log.info("a2a.service.deleted id={}", id);
    }

    private record Validated(String name, String baseUrl, String publicUrl, String apiKeyEncrypted, String tokenUrl,
                             String clientId, String clientSecretEncrypted) {
    }

    private Validated validate(A2AServiceRequest request, A2AServiceStore.StoredService existing) {
        String name = request.name() == null ? "" : request.name().strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("Name is required (at most " + MAX_NAME_LENGTH + " characters)");
        }
        String baseUrl = url(request.baseUrl(), "Base URL");
        if (baseUrl == null) {
            throw badRequest("Base URL is required");
        }
        String publicUrl = url(request.publicUrl(), "Public URL");

        String apiKey;
        if (notBlank(request.apiKey())) {
            apiKey = secrets.encrypt(secret(request.apiKey(), "API key"));
        } else if (existing != null) {
            apiKey = existing.apiKeyEncrypted();
        } else {
            throw badRequest("API key is required");
        }

        String tokenUrl = url(request.callerTokenUrl(), "Caller token URL");
        String clientId = blankToNull(request.callerClientId() == null ? null : request.callerClientId().strip());
        if (tokenUrl == null && clientId == null) {
            if (notBlank(request.callerClientSecret())) {
                throw badRequest("A caller client secret needs a caller token URL and client id");
            }
            return new Validated(name, baseUrl, publicUrl, apiKey, null, null, null);
        }
        if (tokenUrl == null || clientId == null) {
            throw badRequest("Caller identity needs both a token URL and a client id (or neither)");
        }
        if (clientId.length() > 255 || clientId.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))) {
            throw badRequest("Caller client id is invalid");
        }
        String clientSecret;
        if (notBlank(request.callerClientSecret())) {
            clientSecret = secrets.encrypt(secret(request.callerClientSecret(), "Caller client secret"));
        } else if (existing != null && existing.callerClientSecretEncrypted() != null) {
            clientSecret = existing.callerClientSecretEncrypted();
        } else {
            throw badRequest("Caller client secret is required for a caller identity");
        }
        return new Validated(name, baseUrl, publicUrl, apiKey, tokenUrl, clientId, clientSecret);
    }

    /** Absolute http(s) URL with a host and no credentials, query or fragment; trailing slashes removed. */
    static String url(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw badRequest(field + " is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.length() > MAX_URL_LENGTH || !(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw badRequest(field + " must be an http(s) URL with a host and without credentials, query or fragment");
        }
        return trimmed.replaceAll("/+$", "");
    }

    private static String secret(String value, String field) {
        String trimmed = value.strip();
        if (trimmed.length() > MAX_SECRET_LENGTH || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw badRequest(field + " is invalid");
        }
        return trimmed;
    }

    private void rejectConfigured(String id) {
        if (clients.isConfigured(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A2A service '" + id + "' comes from the service configuration and cannot be changed here");
        }
    }

    private void reload() {
        clients.replaceAdded(store.list().stream().map(this::toProvider).toList());
    }

    /** Fail closed: when a secret cannot be decrypted the client gets no API key and is NOT_CONFIGURED. */
    private A2AProvidersProperties.Provider toProvider(A2AServiceStore.StoredService s) {
        var provider = new A2AProvidersProperties.Provider();
        provider.setType(s.type());
        provider.setId(s.id());
        provider.setName(s.name());
        provider.setBaseUrl(s.baseUrl());
        provider.setPublicUrl(s.publicUrl());
        var apiKey = secrets.decrypt(s.apiKeyEncrypted());
        var clientSecret = s.callerClientSecretEncrypted() == null ? java.util.Optional.<String>empty()
                : secrets.decrypt(s.callerClientSecretEncrypted());
        boolean usable = apiKey.isPresent() && (s.callerClientSecretEncrypted() == null || clientSecret.isPresent());
        provider.setApiKey(usable ? apiKey.get() : null);
        if (usable && s.callerTokenUrl() != null) {
            provider.getCallerAuth().setTokenUrl(s.callerTokenUrl());
            provider.getCallerAuth().setClientId(s.callerClientId());
            provider.getCallerAuth().setClientSecret(clientSecret.get());
        }
        return provider;
    }

    private A2AServiceDto toDto(A2AServiceStore.StoredService s) {
        return new A2AServiceDto(s.id(), s.name(), s.type(), "DASHBOARD", true, s.baseUrl(), s.publicUrl(),
                s.apiKeyEncrypted() != null, s.callerTokenUrl(), s.callerClientId(),
                s.callerClientSecretEncrypted() != null, null, null, null, s.updatedAt());
    }

    private A2AServiceDto withStatus(String id, A2AServiceDto dto) {
        var client = clients.find(id);
        if (client.isEmpty()) {
            return copy(dto, "NOT_CONFIGURED", "Not loaded", null);
        }
        if (!client.get().configured() && "DASHBOARD".equals(dto.source()) && !secrets.available()) {
            return copy(dto, "NOT_CONFIGURED", "Its secrets cannot be read: A2A service encryption is not configured", null);
        }
        if (!client.get().configured() && "DASHBOARD".equals(dto.source())) {
            return copy(dto, "NOT_CONFIGURED", "Its secrets cannot be decrypted; enter the API key and caller secret again", null);
        }
        var listing = client.get().listAgents();
        if (listing.status() == A2AProviderClient.Status.OK) {
            return copy(dto, "OK", null, listing.agents().size());
        }
        String status = listing.status() == A2AProviderClient.Status.NOT_FOUND ? "UNAVAILABLE" : listing.status().name();
        return copy(dto, status, listing.reason(), null);
    }

    private static A2AServiceDto copy(A2AServiceDto d, String status, String message, Integer agentCount) {
        return new A2AServiceDto(d.id(), d.name(), d.type(), d.source(), d.editable(), d.baseUrl(), d.publicUrl(),
                d.hasApiKey(), d.callerTokenUrl(), d.callerClientId(), d.hasCallerClientSecret(), status, message,
                agentCount, d.updatedAt());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No A2A service with id '" + id + "'");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }
}
