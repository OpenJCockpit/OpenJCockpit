package nl.metafactory.aicontrol.model;

/**
 * Credential metadata returned to the client for editing — deliberately never includes the
 * secret itself, only whether one is currently set.
 */
public record ProjectGitCredentialDto(
        GitCredentialType credentialType,
        String username,
        String githubApiUrl,
        boolean hasSecret
) {}
