package nl.metafactory.agents.persistence;

/**
 * Utility for sanitising repository URLs before they are persisted to the database.
 *
 * <p>This masks credentials embedded in a URL (e.g.
 * {@code https://user:token@host/repo} becomes {@code https://***@host/repo})
 * before the value is ever persisted to the database.
 *
 * <p>Unlike a similar helper in ai-control-service's {@code GitOperationService}
 * (which returns the literal string {@code "<null>"} for a null input, appropriate
 * only for log messages), this sanitizer returns a real Java {@code null} for a
 * null input so a null repository URL is persisted as SQL NULL rather than a
 * fabricated placeholder string.
 */
public final class RepositoryUrlSanitizer {

    private RepositoryUrlSanitizer() {
        // static-only utility
    }

    /**
     * Masks any embedded credentials in the given URL.
     *
     * @param url the URL to mask, or {@code null}
     * @return the masked URL, or {@code null} if the input was {@code null}
     */
    public static String mask(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("://[^@]+@", "://***@");
    }
}
