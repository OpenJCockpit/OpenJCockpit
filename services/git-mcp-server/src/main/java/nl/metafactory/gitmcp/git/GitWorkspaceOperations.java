package nl.metafactory.gitmcp.git;

import org.eclipse.jgit.transport.CredentialsProvider;

import java.nio.file.Path;

/**
 * Port to the actual git implementation (JGit). The MCP tools talk
 * exclusively through this interface so that the git integration lives in one place and
 * the tool logic can be tested without real repositories.
 */
public interface GitWorkspaceOperations {

    void cloneRepository(String url, String branch, Path target,
                         CredentialsProvider credentials, int timeoutSeconds) throws Exception;

    void checkoutBranch(Path repoPath, String branchName, boolean createBranch) throws Exception;

    void pull(Path repoPath, CredentialsProvider credentials, int timeoutSeconds) throws Exception;

    boolean hasUncommittedChanges(Path repoPath) throws Exception;

    void stageAll(Path repoPath) throws Exception;

    String commitWithMessage(Path repoPath, String message,
                             String authorName, String authorEmail) throws Exception;

    void pushBranch(Path repoPath, String remote, String branch,
                    CredentialsProvider credentials, int timeoutSeconds) throws Exception;

    String currentBranch(Path repoPath) throws Exception;

    /**
     * Ensures that a local head {@code refs/heads/<branch>} exists: if it does not,
     * origin is fetched and the branch is created from {@code origin/<branch>}.
     * Returns {@code false} when the branch exists neither locally nor on origin.
     */
    boolean ensureLocalBranch(Path repoPath, String branch,
                              CredentialsProvider credentials, int timeoutSeconds) throws Exception;
}
