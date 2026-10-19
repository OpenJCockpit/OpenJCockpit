package nl.metafactory.aicontrol.specqueue.runner;

import nl.metafactory.aicontrol.model.GitCredentialType;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.ProjectGitCredential;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.repository.ProjectGitCredentialRepository;
import nl.metafactory.aicontrol.service.CredentialEncryptionService;
import nl.metafactory.aicontrol.service.GitHubPullRequestRef;
import nl.metafactory.aicontrol.specqueue.github.GitHubApiBaseResolver;
import nl.metafactory.aicontrol.specqueue.github.PullRequestUrlParser;
import org.springframework.stereotype.Component;

/** Resolves the GitHub endpoint and token for a stored PR URL, re-validated against the CURRENT project git URL. */
@Component
public class RunnerGitHubAccessStep {

    public sealed interface Access {
        record Ready(GitHubPullRequestRef ref, String apiUrl, String token) implements Access {
            @Override
            public String toString() {
                return "Ready[ref=" + ref + "]";
            }
        }

        record Unusable(SpecQueueFailureReason reason) implements Access {}
    }

    private final ProjectGitCredentialRepository credentials;
    private final CredentialEncryptionService encryption;

    public RunnerGitHubAccessStep(ProjectGitCredentialRepository credentials, CredentialEncryptionService encryption) {
        this.credentials = credentials;
        this.encryption = encryption;
    }

    public Access resolve(Project project, String storedPrUrl) {
        var parsed = PullRequestUrlParser.parse(storedPrUrl, project.getGitUrl());
        if (parsed instanceof PullRequestUrlParser.Invalid invalid) {
            return new Access.Unusable(invalid.reason());
        }
        var valid = (PullRequestUrlParser.Valid) parsed;
        ProjectGitCredential credential = credentials
                .findFirstByProjectIdAndActiveOrderByCreatedAtDesc(project.getId(), (short) 1)
                .filter(c -> c.getCredentialType() != GitCredentialType.NONE)
                .orElse(null);
        String secret = credential == null ? null : decrypt(credential.getEncryptedSecret());
        if (secret == null || secret.isBlank()) {
            return new Access.Unusable(SpecQueueFailureReason.MERGE_AUTH_FAILED);
        }
        return GitHubApiBaseResolver.resolve(valid.host(), credential.getGithubApiUrl())
                .<Access>map(api -> new Access.Ready(valid.ref(), api, secret))
                .orElseGet(() -> new Access.Unusable(SpecQueueFailureReason.PR_HOST_UNSUPPORTED));
    }

    private String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            return encryption.decrypt(encrypted);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
