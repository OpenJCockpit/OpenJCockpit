package nl.metafactory.aicontrol.specqueue.github;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Must stay identical to git-mcp's apiBaseOf. */
public final class GitHubApiBaseResolver {

    private static final Pattern CONFIGURED = Pattern.compile(
            "https?://[A-Za-z0-9.-]{1,253}(?::[0-9]{1,5})?(?:/[A-Za-z0-9._~-]+)*/?");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]{1,253}(?::[0-9]{1,5})?");

    private GitHubApiBaseResolver() {
    }

    public static Optional<String> resolve(String host, String configuredApiUrl) {
        if (configuredApiUrl != null && !configuredApiUrl.isBlank()) {
            String url = configuredApiUrl.strip();
            if (!CONFIGURED.matcher(url).matches()) {
                return Optional.empty();
            }
            String path = url.substring(url.indexOf("://") + 3);
            for (String segment : path.split("/")) {
                if (segment.equals(".") || segment.equals("..")) {
                    return Optional.empty();
                }
            }
            return Optional.of(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        }
        if (host == null || !HOST.matcher(host).matches()) {
            return Optional.empty();
        }
        if (host.toLowerCase(Locale.ROOT).equals("github.com")) {
            return Optional.of("https://api.github.com");
        }
        return Optional.of("https://" + host + "/api/v3");
    }
}
