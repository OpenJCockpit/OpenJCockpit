package nl.metafactory.gitmcp.config;

import tools.jackson.databind.json.JsonMapper;
import nl.metafactory.gitmcp.git.GitHubOperations;
import nl.metafactory.gitmcp.git.GitWorkspaceOperations;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * JGit and GitHub adapters — same pattern as the GitAdapterConfig in the
 * ai-control-service: thin, without logic of their own, and excluded from coverage;
 * all tool logic lives in GitToolsService/GitHubToolsService and is tested against
 * the GitWorkspaceOperations/GitHubOperations interfaces.
 */
@Configuration
public class GitAdapterConfig {

    @Bean
    public GitHubOperations gitHubOperations() {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        var objectMapper = JsonMapper.builder().build();
        return (apiBase, owner, repo, headBranch, baseBranch, title, body, token) -> {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title != null ? title : headBranch,
                    "head", headBranch,
                    "base", baseBranch,
                    "body", body != null ? body : ""));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/repos/" + owner + "/" + repo + "/pulls"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitHub API returned " + response.statusCode()
                        + ": " + response.body());
            }
            return objectMapper.readTree(response.body()).path("html_url").asString();
        };
    }

    @Bean
    public GitWorkspaceOperations gitWorkspaceOperations() {
        return new GitWorkspaceOperations() {

            @Override
            public void cloneRepository(String url, String branch, Path target,
                                        CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                var cmd = Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(target.toFile())
                        .setBranch(branch)
                        .setTimeout(timeoutSeconds);
                if (credentials != null) cmd.setCredentialsProvider(credentials);
                cmd.call().close();
            }

            @Override
            public void checkoutBranch(Path repoPath, String branchName, boolean createBranch) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    git.checkout().setCreateBranch(createBranch).setName(branchName).call();
                }
            }

            @Override
            public void pull(Path repoPath, CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    var cmd = git.pull().setTimeout(timeoutSeconds);
                    if (credentials != null) cmd.setCredentialsProvider(credentials);
                    cmd.call();
                }
            }

            @Override
            public boolean hasUncommittedChanges(Path repoPath) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    return !git.status().call().isClean();
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
                    RevCommit commit = git.commit().setMessage(message)
                            .setAuthor(ident).setCommitter(ident).call();
                    return commit.getName();
                }
            }

            @Override
            public void pushBranch(Path repoPath, String remote, String branch,
                                   CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    var cmd = git.push().setRemote(remote)
                            .setRefSpecs(new RefSpec(branch + ":" + branch))
                            .setTimeout(timeoutSeconds);
                    if (credentials != null) cmd.setCredentialsProvider(credentials);
                    cmd.call();
                }
            }

            @Override
            public String currentBranch(Path repoPath) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    return git.getRepository().getBranch();
                }
            }

            @Override
            public boolean ensureLocalBranch(Path repoPath, String branch,
                                             CredentialsProvider credentials, int timeoutSeconds) throws Exception {
                try (var git = Git.open(repoPath.toFile())) {
                    if (git.getRepository().findRef("refs/heads/" + branch) != null) {
                        return true;
                    }
                    var fetch = git.fetch().setRemote("origin").setTimeout(timeoutSeconds);
                    if (credentials != null) fetch.setCredentialsProvider(credentials);
                    fetch.call();
                    if (git.getRepository().findRef("refs/remotes/origin/" + branch) == null) {
                        return false;
                    }
                    git.checkout().setCreateBranch(true).setName(branch)
                            .setStartPoint("origin/" + branch)
                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                            .call();
                    return true;
                }
            }
        };
    }
}
