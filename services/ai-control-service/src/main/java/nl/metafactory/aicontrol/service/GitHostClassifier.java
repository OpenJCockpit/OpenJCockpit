package nl.metafactory.aicontrol.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a Git URL may be contacted at all (scheme) and describes what the host resolves to.
 * The classification is advisory: it produces warnings, it never blocks a private or loopback host,
 * so on-prem Git servers work without configuration. The decision is made on the resolved IP
 * addresses, not the name, so a DNS name that points at 127.0.0.1 is flagged too.
 */
public final class GitHostClassifier {

    public static final String HOST_LOOPBACK = "host_loopback";
    public static final String HOST_LINK_LOCAL = "host_link_local";
    public static final String HOST_PRIVATE = "host_private";

    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "ssh", "git");
    private static final Pattern SCP_LIKE = Pattern.compile("^[\\w.-]+@([^:/\\s]+):(?!//).+$");

    private GitHostClassifier() {
    }

    /** The host that will be contacted and the warnings for it. {@code host} is safe to log. */
    public record Target(String host, Set<String> warnings) {
        public String classification() {
            return warnings.isEmpty() ? "public" : String.join(",", warnings);
        }
    }

    public static final class RejectedUrlException extends Exception {
        public RejectedUrlException(String message) {
            super(message);
        }
    }

    /** Git remote: https, ssh or git, or scp-style {@code user@host:path} (ssh). */
    public static Target classifyGitUrl(String gitUrl) throws RejectedUrlException {
        return classify(gitUrl, ALLOWED_SCHEMES);
    }

    /** GitHub API base URL: https only, because a token is sent to it. */
    public static Target classifyApiUrl(String apiUrl) throws RejectedUrlException {
        return classify(apiUrl, Set.of("https"));
    }

    private static Target classify(String url, Set<String> schemes) throws RejectedUrlException {
        if (url == null || url.isBlank()) {
            throw new RejectedUrlException("URL is required");
        }
        String trimmed = url.trim();
        String host;
        Matcher scp = SCP_LIKE.matcher(trimmed);
        if (!trimmed.contains("://") && scp.matches()) {
            if (!schemes.contains("ssh")) {
                throw new RejectedUrlException("Unsupported URL scheme");
            }
            host = scp.group(1);
        } else {
            URI uri;
            try {
                uri = new URI(trimmed);
            } catch (URISyntaxException e) {
                throw new RejectedUrlException("Invalid URL");
            }
            String scheme = uri.getScheme();
            if (scheme == null || !schemes.contains(scheme.toLowerCase(Locale.ROOT))) {
                throw new RejectedUrlException("Unsupported URL scheme");
            }
            host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new RejectedUrlException("URL has no host");
            }
        }
        return new Target(host, warningsFor(host));
    }

    private static Set<String> warningsFor(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Not a policy question: the connection attempt itself will fail with a friendly message.
            return Set.of();
        }
        Set<String> warnings = new TreeSet<>();
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                warnings.add(HOST_LOOPBACK);
            } else if (address.isLinkLocalAddress()) {
                warnings.add(HOST_LINK_LOCAL);
            } else if (address.isSiteLocalAddress() || isUniqueLocal(address) || isCarrierGradeNat(address)) {
                warnings.add(HOST_PRIVATE);
            }
        }
        return Collections.unmodifiableSet(warnings);
    }

    private static boolean isUniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
    }
}
