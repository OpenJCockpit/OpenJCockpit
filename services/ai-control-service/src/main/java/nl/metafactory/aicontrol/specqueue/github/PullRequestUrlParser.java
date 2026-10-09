package nl.metafactory.aicontrol.specqueue.github;

import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.service.GitHubPullRequestRef;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates a PR URL against the project's git URL. Also the SSRF guard: no foreign host is ever contacted. */
public final class PullRequestUrlParser {

    private static final int MAX_URL_LENGTH = 500;
    private static final Pattern PR_URL = Pattern.compile(
            "https://([A-Za-z0-9.-]{1,253}(?::[0-9]{1,5})?)/([A-Za-z0-9._-]{1,100})/([A-Za-z0-9._-]{1,100})/pull/([1-9][0-9]{0,9})");
    private static final Pattern PROJECT_URL = Pattern.compile(
            "https://(?:[^/@\\s]+@)?([A-Za-z0-9.-]{1,253}(?::[0-9]{1,5})?)/([A-Za-z0-9._-]{1,100})/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?/?",
            Pattern.CASE_INSENSITIVE);

    private PullRequestUrlParser() {
    }

    public sealed interface Result permits Valid, Invalid {}

    public record Valid(GitHubPullRequestRef ref, String host, String canonicalUrl) implements Result {}

    public record Invalid(SpecQueueFailureReason reason) implements Result {}

    public static Result parse(String prUrl, String projectGitUrl) {
        if (prUrl == null || prUrl.length() > MAX_URL_LENGTH) {
            return invalid(SpecQueueFailureReason.PR_URL_INVALID);
        }
        Matcher pr = PR_URL.matcher(prUrl);
        if (!pr.matches()) {
            return invalid(SpecQueueFailureReason.PR_URL_INVALID);
        }
        String owner = pr.group(2);
        String repo = pr.group(3);
        long number = Long.parseLong(pr.group(4));
        if (number > Integer.MAX_VALUE || isDots(owner) || isDots(repo)) {
            return invalid(SpecQueueFailureReason.PR_URL_INVALID);
        }
        Matcher project = projectGitUrl == null ? null : PROJECT_URL.matcher(projectGitUrl);
        if (project == null || !project.matches()) {
            return invalid(SpecQueueFailureReason.PR_HOST_UNSUPPORTED);
        }
        String host = pr.group(1).toLowerCase(Locale.ROOT);
        if (!host.equals(project.group(1).toLowerCase(Locale.ROOT))
                || !owner.equalsIgnoreCase(project.group(2))
                || !repo.equalsIgnoreCase(project.group(3))) {
            return invalid(SpecQueueFailureReason.PR_URL_INVALID);
        }
        return new Valid(new GitHubPullRequestRef(owner, repo, (int) number), host,
                "https://" + host + "/" + owner + "/" + repo + "/pull/" + number);
    }

    private static boolean isDots(String s) {
        return ".".equals(s) || "..".equals(s);
    }

    private static Invalid invalid(SpecQueueFailureReason reason) {
        return new Invalid(reason);
    }
}
