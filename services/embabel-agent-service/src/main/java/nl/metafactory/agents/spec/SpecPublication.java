package nl.metafactory.agents.spec;

/**
 * Outcome of a publication to git: OK (branch pushed, possibly with a
 * created pull request), SKIPPED or FAILED.
 */
public record SpecPublication(String status, String branch, String pullRequestUrl, String message) {

    public static SpecPublication published(String branch, String message) {
        return new SpecPublication("OK", branch, null, message);
    }

    public static SpecPublication published(String branch, String pullRequestUrl, String message) {
        return new SpecPublication("OK", branch, pullRequestUrl, message);
    }

    public static SpecPublication skipped(String message) {
        return new SpecPublication("SKIPPED", null, null, message);
    }

    public static SpecPublication failed(String branch, String message) {
        return new SpecPublication("FAILED", branch, null, message);
    }

    public boolean isPublished() {
        return "OK".equals(status);
    }
}
