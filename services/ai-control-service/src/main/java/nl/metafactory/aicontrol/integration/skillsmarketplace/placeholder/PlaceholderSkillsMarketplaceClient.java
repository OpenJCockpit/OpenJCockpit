package nl.metafactory.aicontrol.integration.skillsmarketplace.placeholder;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import nl.metafactory.aicontrol.integration.http.GuardedHttpGateway;
import nl.metafactory.aicontrol.integration.http.OutboundHttpResult;
import nl.metafactory.aicontrol.integration.skillsmarketplace.ExternalSkill;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceCatalog;
import nl.metafactory.aicontrol.integration.skillsmarketplace.MarketplaceQuery;
import nl.metafactory.aicontrol.integration.skillsmarketplace.SkillsMarketplaceClient;
import nl.metafactory.aicontrol.model.SkillCatalogSourceOutcome;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one placeholder-contract implementation of the adapter seam (ADR-1). Owns everything
 * specific to the placeholder contract: URL composition (BR-15), header shape, wire model,
 * field mapping, and response classification (architecture §6.2/§6.3). Reaches the network
 * <em>only</em> through {@link GuardedHttpGateway} — it holds no {@code HttpClient}, {@code
 * WebClient} or {@code RestClient} field of its own (BR-9, AC-27b).
 */
@Component
public class PlaceholderSkillsMarketplaceClient implements SkillsMarketplaceClient {

    private static final String SKILLS_RESOURCE = "/skills";

    // A private, adapter-owned mapper — never Boot's shared ObjectMapper bean (mirrors the
    // existing WebClientConfig habit of building its own mapper). Unknown fields are ignored so
    // that a slightly-richer real vendor response does not fail mapping (AC-44).
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final GuardedHttpGateway gateway;
    private final SkillsMarketplaceProperties properties;

    public PlaceholderSkillsMarketplaceClient(GuardedHttpGateway gateway, SkillsMarketplaceProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    @Override
    public MarketplaceCatalog fetchCatalog(MarketplaceQuery query) {
        URI uri;
        try {
            uri = composeUri(query.marketplaceUrl());
        } catch (IllegalArgumentException e) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.CONFIG_ERROR, null);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.put("Authorization", "Bearer " + query.apiKey());
        headers.put("User-Agent", "metafactory-ai-control");

        String connectionId = query.connectionId() != null ? query.connectionId().toString() : null;
        OutboundHttpResult result = gateway.get(uri, headers, connectionId, query.connectionName());

        return switch (result) {
            case OutboundHttpResult.Failure failure -> classifyFailure(failure);
            case OutboundHttpResult.Response response -> classifyResponse(response);
        };
    }

    /**
     * BR-15: trim, strip <em>all</em> trailing slashes, append {@code /skills}. {@code
     * https://x.com}, {@code https://x.com/} and {@code https://x.com//} all normalize to {@code
     * https://x.com/skills}; {@code https://x.com/api/} normalizes to {@code
     * https://x.com/api/skills}.
     */
    private URI composeUri(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("blank marketplaceUrl");
        }
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        String withoutTrailingSlashes = trimmed.substring(0, end);
        return URI.create(withoutTrailingSlashes + SKILLS_RESOURCE);
    }

    private MarketplaceCatalog classifyFailure(OutboundHttpResult.Failure failure) {
        SkillCatalogSourceOutcome outcome = switch (failure.kind()) {
            case BLOCKED_BY_POLICY -> SkillCatalogSourceOutcome.BLOCKED_BY_POLICY;
            case TIMEOUT -> SkillCatalogSourceOutcome.TIMEOUT;
            case UNREACHABLE -> SkillCatalogSourceOutcome.UNREACHABLE;
            case RESPONSE_TOO_LARGE, TOO_MANY_REDIRECTS, MALFORMED_TARGET -> SkillCatalogSourceOutcome.INVALID_RESPONSE;
        };
        return MarketplaceCatalog.failure(outcome, failure.httpStatus());
    }

    private MarketplaceCatalog classifyResponse(OutboundHttpResult.Response response) {
        int status = response.status();
        if (status == 401 || status == 403) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.AUTH_FAILED, status);
        }
        if (status < 200 || status > 299) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.INVALID_RESPONSE, status);
        }
        if (!isJsonContentType(response.contentType())) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.INVALID_RESPONSE, status);
        }

        List<PlaceholderSkillItem> items;
        try {
            items = MAPPER.readValue(response.body(), new TypeReference<List<PlaceholderSkillItem>>() {
            });
        } catch (JacksonException e) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.INVALID_RESPONSE, status);
        }

        if (items.size() > properties.getMaxItems()) {
            return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.INVALID_RESPONSE, status);
        }

        List<ExternalSkill> skills = new ArrayList<>(items.size());
        for (PlaceholderSkillItem item : items) {
            if (item.name() == null || item.name().isBlank()) {
                return MarketplaceCatalog.failure(SkillCatalogSourceOutcome.INVALID_RESPONSE, status);
            }
            skills.add(new ExternalSkill(item.name(), item.description()));
        }

        return MarketplaceCatalog.success(status, List.copyOf(skills));
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return base.equals(MediaType.APPLICATION_JSON_VALUE);
    }
}
