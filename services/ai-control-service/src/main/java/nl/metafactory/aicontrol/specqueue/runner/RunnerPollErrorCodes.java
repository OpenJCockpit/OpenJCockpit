package nl.metafactory.aicontrol.specqueue.runner;

/** Fixed strings shown by the API; never remote messages. */
public final class RunnerPollErrorCodes {

    public static final String EMBABEL_UNAVAILABLE = "EMBABEL_UNAVAILABLE";
    public static final String EMBABEL_UNAUTHORIZED = "EMBABEL_UNAUTHORIZED";
    public static final String GITHUB_UNAVAILABLE = "GITHUB_UNAVAILABLE";
    public static final String GITHUB_RATE_LIMITED = "GITHUB_RATE_LIMITED";
    public static final String RUNNER_NOT_CONFIGURED = "RUNNER_NOT_CONFIGURED";

    private RunnerPollErrorCodes() {
    }

    public static String forEmbabel(EmbabelRunnerClient.FailureCode code) {
        return code == EmbabelRunnerClient.FailureCode.UNAUTHORIZED || code == EmbabelRunnerClient.FailureCode.FORBIDDEN
                ? EMBABEL_UNAUTHORIZED : EMBABEL_UNAVAILABLE;
    }

    public static String forNotSent(EmbabelRunnerClient.NotSentReason reason) {
        return switch (reason) {
            case UNCONFIGURED -> RUNNER_NOT_CONFIGURED;
            case UNAUTHORIZED, FORBIDDEN -> EMBABEL_UNAUTHORIZED;
            case CONNECT_FAILED -> EMBABEL_UNAVAILABLE;
        };
    }

    public static String forGitHub(nl.metafactory.aicontrol.service.GitHubApiException e) {
        return e.rateLimited() ? GITHUB_RATE_LIMITED : GITHUB_UNAVAILABLE;
    }
}
