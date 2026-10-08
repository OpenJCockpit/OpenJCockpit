package nl.metafactory.aicontrol.service;

/**
 * Domain error for the skills-marketplace-connections resource, mirroring
 * {@link GitWorkspaceException}'s error-code-enum pattern (see ADR-3 in the
 * skills-marketplace-settings architecture doc).
 */
public class SkillsMarketplaceException extends RuntimeException {

    public enum Code { NOT_FOUND, NAME_CONFLICT, API_KEY_REQUIRED }

    private final Code code;

    public SkillsMarketplaceException(Code code, String details) {
        super(details);
        this.code = code;
    }

    public Code getCode() { return code; }
}
