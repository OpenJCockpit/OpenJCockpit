package nl.metafactory.aicontrol.service;

import org.eclipse.jgit.transport.CredentialsProvider;

import java.nio.file.Path;
import java.util.List;

public interface GitWorkspaceOperations {

    void cloneRepository(String url, String branch, Path target,
                         CredentialsProvider credentials, int timeoutSeconds) throws Exception;

    List<String> listRemoteBranches(String url, CredentialsProvider credentials,
                                    int timeoutSeconds) throws Exception;

    void createAndCheckoutBranch(Path repoPath, String branchName) throws Exception;

    boolean hasUncommittedChanges(Path repoPath) throws Exception;

    void stageAll(Path repoPath) throws Exception;

    String commitWithMessage(Path repoPath, String message,
                             String authorName, String authorEmail) throws Exception;

    void pushBranch(Path repoPath, String remote, String branch,
                    CredentialsProvider credentials, int timeoutSeconds) throws Exception;

    String getHeadCommitHash(Path repoPath) throws Exception;
}
