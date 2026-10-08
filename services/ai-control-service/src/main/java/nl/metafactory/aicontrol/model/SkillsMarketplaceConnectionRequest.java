package nl.metafactory.aicontrol.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request shape for creating/updating a skills marketplace connection.
 *
 * <p>The contract's {@code maxLength}/{@code required}/{@code pattern} constraints produce no
 * runtime validation because {@code schemaMappings} redirects this schema to this handwritten
 * record (see architecture §6.4 / risk A2) — these annotations are the actual enforcement.</p>
 *
 * <p>{@code apiKey} is intentionally not {@code @NotBlank} here: it is required on create but
 * optional on update (secret-retention semantics, BR-2), which is a cross-field/cross-operation
 * rule enforced in {@code SkillsMarketplaceConnectionService}, not in bean validation.</p>
 */
public record SkillsMarketplaceConnectionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://\\S+$") String marketplaceUrl,
        @Size(max = 4096) String apiKey,
        @Size(max = 500) String description,
        boolean enabled
) {}
