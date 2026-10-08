package nl.metafactory.aicontrol.integration.skillsmarketplace;

/**
 * The internal, vendor-neutral skill representation that crosses the adapter seam outward (ADR-1,
 * BR-13). Contains only what every vendor is expected to be able to supply; never the placeholder
 * wire type.
 */
public record ExternalSkill(String name, String description) {
}
