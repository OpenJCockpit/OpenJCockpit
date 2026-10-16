package nl.metafactory.agents.definition.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The single immutable snapshot an agent run executes against. Built once by the resolver before
 * Embabel starts; every collection is defensively copied so later registry changes cannot leak
 * into a running process.
 *
 * @param guardrailExecutors guardrail id → name of the executor that actually enforces it
 * @param attempts           the source walk that produced this snapshot (explainability only)
 */
public record ResolvedAgentDefinition(
        String agentId,
        String tenantId,
        String instructions,
        String modelAlias,
        List<String> skillIds,
        List<String> toolIds,
        List<String> guardrailIds,
        DefinitionSource source,
        String version,
        String contentHash,
        Instant resolvedAt,
        Map<String, String> guardrailExecutors,
        List<SourceAttempt> attempts
) {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public ResolvedAgentDefinition {
        requireText(agentId, "agentId");
        requireText(tenantId, "tenantId");
        requireText(instructions, "instructions");
        requireText(modelAlias, "modelAlias");
        requireText(version, "version");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        if (contentHash == null || !SHA256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hex digest");
        }
        skillIds = List.copyOf(skillIds == null ? List.of() : skillIds);
        toolIds = List.copyOf(toolIds == null ? List.of() : toolIds);
        guardrailIds = List.copyOf(guardrailIds == null ? List.of() : guardrailIds);
        guardrailExecutors = Map.copyOf(guardrailExecutors == null ? Map.of() : guardrailExecutors);
        attempts = List.copyOf(attempts == null ? List.of() : attempts);
        for (String guardrail : guardrailIds) {
            if (!guardrailExecutors.containsKey(guardrail)) {
                throw new IllegalArgumentException("guardrail " + guardrail + " has no enforcing executor");
            }
        }
    }

    /** Never renders instructions: snapshots end up in logs via string concatenation. */
    @Override
    public String toString() {
        return "ResolvedAgentDefinition{agentId=" + agentId + ", tenantId=" + tenantId + ", version=" + version
                + ", source=" + source + ", contentHash=" + contentHash + ", modelAlias=" + modelAlias
                + ", skills=" + skillIds + ", tools=" + toolIds + ", guardrails=" + guardrailIds
                + ", resolvedAt=" + resolvedAt + "}";
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
