package nl.metafactory.aicontrol.integration.http;

import jakarta.annotation.PostConstruct;
import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The single host/IP policy decision point for marketplace egress (BR-14). {@link
 * #denialReason(URI, String, String)} returns {@link Optional#empty()} to allow, or a
 * human-readable reason to deny. Cloud-metadata endpoints and a small set of always-reserved
 * ranges are denied unconditionally, in both flag states (ADR-7); everything else in {@link
 * #RELAXABLE_CIDRS} (loopback, RFC1918, link-local, CGNAT, IPv6 ULA, any-local) is denied unless
 * {@link SkillsMarketplaceProperties.Egress#isAllowPrivateAddresses()} is {@code true}.
 *
 * <p>DNS resolution failure is deliberately treated as "no policy opinion" (allow): a hostname
 * that cannot be resolved is not a policy violation, and {@link GuardedHttpGateway}'s own connect
 * attempt will fail naturally and be classified {@code UNREACHABLE} — this guard must not
 * misclassify a network problem as a policy block.</p>
 */
@Component
public class EgressGuard {

    private static final Logger log = LoggerFactory.getLogger(EgressGuard.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final List<InetAddress> METADATA_ADDRESSES = List.of(
            literal("169.254.169.254"),
            literal("169.254.169.253"),
            literal("100.100.100.200"),
            literal("fd00:ec2::254")
    );

    // Always denied, in both flag states: multicast is handled separately via InetAddress, and
    // these reserved/documentation/benchmark ranges are never legitimate marketplace targets.
    private static final List<Cidr> ALWAYS_DENIED_CIDRS = List.of(
            cidr("192.0.2.0/24"),
            cidr("198.18.0.0/15"),
            cidr("198.51.100.0/24"),
            cidr("203.0.113.0/24"),
            cidr("240.0.0.0/4")
    );

    // Relaxable only under the allow-private-addresses opt-in.
    private static final List<Cidr> RELAXABLE_CIDRS = List.of(
            cidr("127.0.0.0/8"),
            cidr("::1/128"),
            cidr("0.0.0.0/32"),
            cidr("::/128"),
            cidr("10.0.0.0/8"),
            cidr("172.16.0.0/12"),
            cidr("192.168.0.0/16"),
            cidr("169.254.0.0/16"),
            cidr("fe80::/10"),
            cidr("100.64.0.0/10"),
            cidr("fc00::/7")
    );

    private final SkillsMarketplaceProperties properties;

    public EgressGuard(SkillsMarketplaceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logRelaxedPostureAtStartup() {
        if (properties.getEgress().isAllowPrivateAddresses()) {
            log.warn("Skills marketplace egress guard is RELAXED at startup: loopback/private/link-local "
                    + "addresses are permitted (allow-private-addresses=true). This must never be enabled "
                    + "in a deployed environment.");
        }
    }

    /**
     * @return empty when the target is allowed; otherwise a human-readable denial reason.
     *         {@code connectionId}/{@code connectionName} are used only for logging context.
     */
    public Optional<String> denialReason(URI uri, String connectionId, String connectionName) {
        String reason = evaluate(uri, connectionId, connectionName);
        if (reason != null) {
            log.warn("Skills marketplace egress denied connectionId={} connectionName={} target={} reason={}",
                    connectionId, connectionName, reducedTarget(uri), reason);
            return Optional.of(reason);
        }
        return Optional.empty();
    }

    private String evaluate(URI uri, String connectionId, String connectionName) {
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return "unsupported scheme";
        }
        if (uri.getUserInfo() != null) {
            return "userinfo not permitted in marketplace URL";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "missing host";
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Not a policy decision — let the gateway's own connect attempt fail as UNREACHABLE.
            return null;
        }

        for (InetAddress raw : addresses) {
            InetAddress address = unwrapIpv4Mapped(raw);
            if (isMetadataAddress(address)) {
                return "cloud metadata address";
            }
            if (address.isMulticastAddress()) {
                return "multicast address";
            }
            if (matchesAny(address, ALWAYS_DENIED_CIDRS)) {
                return "reserved/documentation/benchmark address range";
            }
            if (matchesAny(address, RELAXABLE_CIDRS)) {
                if (!properties.getEgress().isAllowPrivateAddresses()) {
                    return "loopback/private/link-local address (allow-private-addresses is disabled)";
                }
                log.warn("Skills marketplace egress permitted for private/local address connectionId={} "
                                + "connectionName={} target={} address={} because allow-private-addresses is enabled",
                        connectionId, connectionName, reducedTarget(uri), address.getHostAddress());
            }
        }
        return null;
    }

    private static boolean isMetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return METADATA_ADDRESSES.stream().anyMatch(m -> Arrays.equals(m.getAddress(), bytes));
    }

    private static boolean matchesAny(InetAddress address, List<Cidr> cidrs) {
        return cidrs.stream().anyMatch(c -> c.contains(address));
    }

    // Package-private (not private) purely so a unit test can exercise it directly with a
    // manually-constructed Inet6Address: java.net.InetAddress's own literal-string parser already
    // collapses a textual "::ffff:a.b.c.d" into a plain Inet4Address before this code ever sees
    // it, so this path is only reachable in practice via a raw AAAA-style DNS answer, which cannot
    // be produced deterministically offline.
    static InetAddress unwrapIpv4Mapped(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                return ipv4From(Arrays.copyOfRange(bytes, 12, 16), address);
            }
        }
        return address;
    }

    // Package-private so a unit test can prove the fallback behaviour with a deliberately
    // invalid-length array; the only real call site above always passes exactly 4 bytes, which
    // InetAddress.getByAddress(byte[]) can never reject.
    static InetAddress ipv4From(byte[] ipv4Bytes, InetAddress fallback) {
        try {
            return InetAddress.getByAddress(ipv4Bytes);
        } catch (UnknownHostException e) {
            return fallback;
        }
    }

    /**
     * Reduces a URI to {@code scheme://host[:port]} for safe logging — never path, query,
     * fragment or userinfo (BR-8). Package-private so {@link GuardedHttpGateway} can reuse it.
     */
    static String reducedTarget(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        String scheme = uri.getScheme() != null ? uri.getScheme() : "unknown";
        String host = uri.getHost() != null ? uri.getHost() : "unknown";
        int port = uri.getPort();
        return port >= 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
    }

    // Package-private (not private) so a unit test can prove the wrapping behaviour directly by
    // passing a deliberately-unresolvable literal; every actual call site above uses a hardcoded,
    // always-valid IP literal, so this catch is never reached via the class's own static
    // initialisation.
    static InetAddress literal(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Cidr cidr(String value) {
        String[] parts = value.split("/", 2);
        try {
            InetAddress network = InetAddress.getByName(parts[0]);
            return new Cidr(network.getAddress(), Integer.parseInt(parts[1]));
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private record Cidr(byte[] network, int prefixLength) {
        boolean contains(InetAddress address) {
            byte[] addr = address.getAddress();
            if (addr.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }
    }
}
