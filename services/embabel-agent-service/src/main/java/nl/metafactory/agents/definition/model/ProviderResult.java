package nl.metafactory.agents.definition.model;

import java.util.Objects;

/** What one {@code AgentDefinitionProvider} returned for one agent id. */
public record ProviderResult(SourceStatus status, AgentDefinitionDocument document, String reason) {

    public ProviderResult {
        Objects.requireNonNull(status, "status");
        if ((status == SourceStatus.FOUND) != (document != null)) {
            throw new IllegalArgumentException("a document is present if and only if status is FOUND");
        }
    }

    public static ProviderResult found(AgentDefinitionDocument document) {
        return new ProviderResult(SourceStatus.FOUND, document, null);
    }

    public static ProviderResult of(SourceStatus status, String reason) {
        return new ProviderResult(status, null, reason);
    }
}
