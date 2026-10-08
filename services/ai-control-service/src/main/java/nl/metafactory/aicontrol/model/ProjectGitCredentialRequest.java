package nl.metafactory.aicontrol.model;

public record ProjectGitCredentialRequest(
        GitCredentialType credentialType,
        String username,
        String secret,
        String githubApiUrl
) {}
