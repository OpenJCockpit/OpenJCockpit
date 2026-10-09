package nl.metafactory.aicontrol.service;

/** Status 0 = transport failure or timeout. The message never carries remote content. */
public class GitHubApiException extends Exception {

    private final int status;
    private final boolean rateLimited;

    public GitHubApiException(int status, boolean rateLimited) {
        super("HTTP " + status);
        this.status = status;
        this.rateLimited = rateLimited;
    }

    public GitHubApiException(Throwable transportCause) {
        super("GitHub API transport failure: " + transportCause.getClass().getSimpleName());
        this.status = 0;
        this.rateLimited = false;
    }

    public int status() {
        return status;
    }

    public boolean rateLimited() {
        return rateLimited;
    }
}
