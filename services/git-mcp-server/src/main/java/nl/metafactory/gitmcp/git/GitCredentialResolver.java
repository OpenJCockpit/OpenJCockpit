package nl.metafactory.gitmcp.git;

import java.util.Optional;

/**
 * Resolves the git credentials of a workspace server-side, so that no credential is ever a tool
 * argument (and therefore never reaches an LLM, a tool-call log or the LiteLLM gateway).
 *
 * <p>Only keyed workspaces ({@code workspaceKey}, e.g. {@code job-<uuid>} of a git-workspace job)
 * have credentials; the shared, unkeyed workspace is always anonymous.
 */
public interface GitCredentialResolver {

    /** Username and token of a workspace. {@link #toString()} never prints the token. */
    record GitCredentials(String username, String token) {
        @Override
        public String toString() {
            return "GitCredentials[username=" + username + ", token=***]";
        }
    }

    /**
     * Credentials for {@code workspaceKey} against {@code repositoryUrl}; empty for anonymous access.
     * The owner of the credentials confirms that the workspace is active and belongs to exactly that
     * repository, so a caller cannot redirect a workspace's credentials to another remote.
     */
    Optional<GitCredentials> resolve(String workspaceKey, String repositoryUrl);
}
