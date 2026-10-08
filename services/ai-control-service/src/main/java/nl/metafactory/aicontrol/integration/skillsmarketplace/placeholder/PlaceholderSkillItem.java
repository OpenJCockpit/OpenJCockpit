package nl.metafactory.aicontrol.integration.skillsmarketplace.placeholder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The placeholder contract's wire shape — {@code {"name": "...", "description": "..."}}.
 * Deliberately package-private: BR-13/AC-49 require that this type never appear in any signature,
 * field, generic parameter, or contract schema outside this package. That is enforced by the Java
 * compiler here, not by review.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PlaceholderSkillItem(String name, String description) {
}
