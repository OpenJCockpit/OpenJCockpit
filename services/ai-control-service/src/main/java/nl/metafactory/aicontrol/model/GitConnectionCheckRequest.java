package nl.metafactory.aicontrol.model;

/** Carries a plain secret for one call only. Never log or return it; {@code toString} hides it. */
public record GitConnectionCheckRequest(
        String gitUrl,
        GitCredentialType credentialType,
        String username,
        String secret,
        String githubApiUrl
) {
    @Override
    public String toString() {
        return "GitConnectionCheckRequest[credentialType=" + credentialType + ", secret=<redacted>]";
    }
}
