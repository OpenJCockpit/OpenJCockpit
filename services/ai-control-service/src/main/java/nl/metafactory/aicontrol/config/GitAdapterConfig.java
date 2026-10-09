package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.service.GitHubApiPort;
import nl.metafactory.aicontrol.service.GitHubPort;
import nl.metafactory.aicontrol.service.GitWorkspaceOperations;
import nl.metafactory.aicontrol.service.JGitPort;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Configuration
public class GitAdapterConfig {

    private final SpecQueueProperties specQueueProperties;

    @Autowired
    public GitAdapterConfig(SpecQueueProperties specQueueProperties) {
        this.specQueueProperties = specQueueProperties;
    }

    /** For tests outside a Spring context. */
    public GitAdapterConfig() {
        this(new SpecQueueProperties());
    }

    @Bean
    public JGitPort jGitPort() {
        return (url, credentials, timeoutSeconds) -> {
            var cmd = Git.lsRemoteRepository().setRemote(url).setTimeout(timeoutSeconds);
            if (credentials != null) cmd.setCredentialsProvider(credentials);
            cmd.call();
        };
    }

    @Bean
    public GitHubPort gitHubPort() {
        return new GitHubApiPort(specQueueProperties.getRunner().getGithubTimeout());
    }

    @Bean
    public GitWorkspaceOperations gitWorkspaceOperations() {
        return new GitWorkspaceOperations() {

            @Override
            public void cloneRepository(String url, String branch, Path target,
                                        CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                var monitor = new TextProgressMonitor(new PrintWriter(System.out));
                var cmd = Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(target.toFile())
                        .setBranch(branch)
                        .setTimeout(timeoutSeconds)
                        .setProgressMonitor(monitor);
                if (credentials != null) cmd.setCredentialsProvider(credentials);
                // Git.close() also closes the underlying Repository; closing it explicitly as well
                // triggers JGit's "close() called when useCnt is already zero" warning.
                cmd.call().close();
            }

            @Override
            public java.util.List<String> listRemoteBranches(String url, CredentialsProvider credentials,
                                                             int timeoutSeconds) throws Exception {
                var cmd = Git.lsRemoteRepository().setRemote(url).setHeads(true).setTimeout(timeoutSeconds);
                if (credentials != null) cmd.setCredentialsProvider(credentials);
                return cmd.call().stream()
                        .map(ref -> ref.getName().replaceFirst("^refs/heads/", ""))
                        .toList();
            }

            @Override
            public void createAndCheckoutBranch(Path repoPath, String branchName) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    git.checkout()
                       .setCreateBranch(true)
                       .setName(branchName)
                       .call();
                }
            }

            @Override
            public boolean hasUncommittedChanges(Path repoPath) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    var status = git.status().call();
                    return !status.isClean();
                }
            }

            @Override
            public void stageAll(Path repoPath) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    git.add().addFilepattern(".").call();
                    git.add().addFilepattern(".").setUpdate(true).call();
                }
            }

            @Override
            public String commitWithMessage(Path repoPath, String message,
                                            String authorName, String authorEmail) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    var ident = new PersonIdent(authorName, authorEmail);
                    RevCommit commit = git.commit()
                            .setMessage(message)
                            .setAuthor(ident)
                            .setCommitter(ident)
                            .call();
                    return commit.getName();
                }
            }

            @Override
            public void pushBranch(Path repoPath, String remote, String branch,
                                   CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    var cmd = git.push()
                            .setRemote(remote)
                            .setRefSpecs(new RefSpec(branch + ":" + branch))
                            .setTimeout(timeoutSeconds);
                    if (credentials != null) cmd.setCredentialsProvider(credentials);
                    var results = cmd.call();
                    for (var result : results) {
                        for (var update : result.getRemoteUpdates()) {
                            if (update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                                update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                                throw new RuntimeException("Push rejected: " + update.getStatus() + " " + update.getMessage());
                            }
                        }
                    }
                }
            }

            @Override
            public String getHeadCommitHash(Path repoPath) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    return git.getRepository().resolve("HEAD").getName();
                }
            }
        };
    }
}