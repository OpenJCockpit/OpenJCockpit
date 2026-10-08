package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.config.AgenticWorkflowProperties;
import nl.metafactory.aicontrol.model.GitWorkspaceJobErrorCode;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

import static nl.metafactory.aicontrol.model.GitCredentialType.NONE;

@Service
public class GitOperationService {

    private final GitWorkspaceOperations gitOps;
    private final CredentialEncryptionService encryption;
    private final AgenticWorkflowProperties properties;

    public GitOperationService(GitWorkspaceOperations gitOps,
                               CredentialEncryptionService encryption,
                               AgenticWorkflowProperties properties) {
        this.gitOps = gitOps;
        this.encryption = encryption;
        this.properties = properties;
    }

    public void cloneDefaultBranch(String gitUrl, String branch, Path targetDir,
                                   ProjectGitCredential credential) {
        CredentialsProvider creds = buildCredentials(credential);
        try {
            gitOps.cloneRepository(gitUrl, branch, targetDir, creds,
                    properties.getCloneTimeoutSeconds());
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED,
                    maskUrl(gitUrl) + ": " + rootMessage(e), e);
        }
    }

    public void checkoutNewBranch(Path repoPath, String branchName) {
        try {
            gitOps.createAndCheckoutBranch(repoPath, branchName);
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.BRANCH_CREATE_FAILED,
                    branchName, e);
        }
    }

    public boolean hasChanges(Path repoPath) {
        try {
            return gitOps.hasUncommittedChanges(repoPath);
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_CLONE_FAILED,
                    "has-changes check failed", e);
        }
    }

    public void stageAll(Path repoPath) {
        try {
            gitOps.stageAll(repoPath);
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.COMMIT_FAILED,
                    "stage-all failed", e);
        }
    }

    public String commit(Path repoPath, String message, String author, String email) {
        try {
            return gitOps.commitWithMessage(repoPath, message, author, email);
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.COMMIT_FAILED,
                    "commit failed", e);
        }
    }

    public void pushBranch(Path repoPath, String remote, String branch,
                           ProjectGitCredential credential) {
        CredentialsProvider creds = buildCredentials(credential);
        try {
            gitOps.pushBranch(repoPath, remote, branch, creds,
                    properties.getPushTimeoutSeconds());
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.PUSH_FAILED,
                    "push to " + branch + " failed: " + rootMessage(e), e);
        }
    }

    public java.util.List<String> listRemoteBranches(String gitUrl, ProjectGitCredential credential) {
        CredentialsProvider creds = buildCredentials(credential);
        try {
            return java.util.List.copyOf(gitOps.listRemoteBranches(gitUrl, creds,
                    properties.getCloneTimeoutSeconds()));
        } catch (Exception e) {
            throw new GitWorkspaceException(GitWorkspaceJobErrorCode.GIT_REPOSITORY_UNAVAILABLE,
                    maskUrl(gitUrl) + ": " + rootMessage(e), e);
        }
    }

    public String getHeadCommitHash(Path repoPath) {
        try {
            return gitOps.getHeadCommitHash(repoPath);
        } catch (Exception e) {
            return null;
        }
    }

    CredentialsProvider buildCredentials(ProjectGitCredential cred) {
        if (cred == null || cred.getCredentialType() == NONE) return null;
        String secret = cred.getEncryptedSecret() != null
                ? encryption.decrypt(cred.getEncryptedSecret()) : null;
        String username = cred.getUsername() != null ? cred.getUsername() : "token";
        String password = secret != null ? secret : "";
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    String maskUrl(String url) {
        if (url == null) return "<null>";
        return url.replaceAll("://[^@]+@", "://***@");
    }

    String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return maskUrl(message);
    }
}