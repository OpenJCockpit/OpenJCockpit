package nl.metafactory.aicontrol.integration.http;

import nl.metafactory.aicontrol.config.SkillsMarketplaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EgressGuardTest {

    private EgressGuard guardDenying() {
        return new EgressGuard(new SkillsMarketplaceProperties());
    }

    private EgressGuard guardAllowingPrivate() {
        var properties = new SkillsMarketplaceProperties();
        properties.getEgress().setAllowPrivateAddresses(true);
        return new EgressGuard(properties);
    }

    // ── scheme / userinfo — always denied ───────────────────────────────────

    @Test
    void deniesNonHttpScheme() {
        var reason = guardAllowingPrivate().denialReason(URI.create("ftp://example.com/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesFileScheme() {
        var reason = guardAllowingPrivate().denialReason(URI.create("file:///etc/passwd"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesUserInfoInUrl() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://user:pass@8.8.8.8/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesMissingHost() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http:///skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    // ── DNS failure is not a policy decision ────────────────────────────────

    @Test
    void allowsWhenHostnameCannotBeResolved() {
        var reason = guardDenying().denialReason(
                URI.create("http://this-host-does-not-exist-12345.invalid/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── loopback / any-local — relaxable ────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.0.5", "0.0.0.0"})
    void deniesLoopbackAndAnyLocalIpv4ByDefault(String host) {
        var reason = guardDenying().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesLoopbackIpv6ByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://[::1]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesAnyLocalIpv6ByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://[::]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void allowsLoopbackWhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://127.0.0.1:9099/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── RFC1918 — relaxable ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.1.1"})
    void deniesRfc1918ByDefault(String host) {
        var reason = guardDenying().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "172.16.0.1", "192.168.1.1"})
    void allowsRfc1918WhenFlagIsEnabled(String host) {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── link-local — relaxable, but the metadata IP inside it is not ────────

    @Test
    void deniesLinkLocalIpv4ByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://169.254.1.1/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void allowsOrdinaryLinkLocalIpv4WhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://169.254.1.1/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    @Test
    void deniesLinkLocalIpv6ByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://[fe80::1]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void allowsLinkLocalIpv6WhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://[fe80::1]/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── CGNAT — relaxable ────────────────────────────────────────────────────

    @Test
    void deniesCgnatByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://100.64.0.1/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void allowsCgnatWhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://100.64.0.1/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── IPv6 ULA — relaxable ─────────────────────────────────────────────────

    @Test
    void deniesIpv6UlaByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://[fc00::1]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void allowsIpv6UlaWhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://[fc00::1]/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── ADR-7: cloud-metadata addresses are hard-blocked in BOTH flag states ──

    @ParameterizedTest
    @ValueSource(strings = {"169.254.169.254", "169.254.169.253", "100.100.100.200"})
    void deniesMetadataAddressesEvenWhenFlagIsDisabled(String host) {
        var reason = guardDenying().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"169.254.169.254", "169.254.169.253", "100.100.100.200"})
    void deniesMetadataAddressesEvenWhenFlagIsEnabled(String host) {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesIpv6MetadataAddressEvenWhenFlagIsDisabled() {
        var reason = guardDenying().denialReason(URI.create("http://[fd00:ec2::254]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void deniesIpv6MetadataAddressEvenWhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://[fd00:ec2::254]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    // ── multicast / reserved / documentation / benchmark — always denied ────

    @Test
    void deniesMulticastAddress() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://224.0.0.1/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1"})
    void deniesReservedDocumentationAndBenchmarkRangesEvenWhenFlagIsEnabled(String host) {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://" + host + "/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    // ── IPv4-mapped IPv6 unwrapping ──────────────────────────────────────────

    @Test
    void unwrapsIpv4MappedIpv6LoopbackAndDeniesByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://[::ffff:127.0.0.1]/skills"), "id", "name");

        assertThat(reason).isPresent();
    }

    @Test
    void unwrapsIpv4MappedIpv6LoopbackAndAllowsWhenFlagIsEnabled() {
        var reason = guardAllowingPrivate().denialReason(URI.create("http://[::ffff:127.0.0.1]/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    @Test
    void unwrapsIpv4MappedIpv6PublicAddressAndAllows() {
        var reason = guardDenying().denialReason(URI.create("http://[::ffff:8.8.8.8]/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── public addresses ─────────────────────────────────────────────────────

    @Test
    void allowsPublicIpv4AddressByDefault() {
        var reason = guardDenying().denialReason(URI.create("http://8.8.8.8/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    @Test
    void allowsHttpsScheme() {
        var reason = guardDenying().denialReason(URI.create("https://8.8.8.8/skills"), "id", "name");

        assertThat(reason).isEmpty();
    }

    // ── startup posture logging (@PostConstruct) ─────────────────────────────

    @Test
    void postConstructDoesNotThrowWhenDenyingByDefault() {
        var guard = guardDenying();

        guard.logRelaxedPostureAtStartup();
    }

    @Test
    void postConstructDoesNotThrowWhenRelaxed() {
        var guard = guardAllowingPrivate();

        guard.logRelaxedPostureAtStartup();
    }

    // ── unit coverage of internal helpers not reachable via URI-literal parsing ──

    @Test
    void unwrapIpv4MappedHandlesARawInet6AddressCarryingAnIpv4MappedPayload() throws Exception {
        // java.net.InetAddress's own textual parser (and even InetAddress.getByAddress(byte[16]))
        // already collapses an IPv4-mapped payload into a plain Inet4Address before EgressGuard
        // ever sees it (verified empirically) — Inet6Address.getByAddress(...) is the one public
        // JDK factory that bypasses that collapsing, which is what makes this defensive path
        // (relevant to a raw AAAA-shaped DNS answer) directly exercisable at all.
        var rawMappedV6 = java.net.Inet6Address.getByAddress(null, new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 127, 0, 0, 1
        }, -1);
        assertThat(rawMappedV6).isInstanceOf(java.net.Inet6Address.class);

        var unwrapped = EgressGuard.unwrapIpv4Mapped(rawMappedV6);

        assertThat(unwrapped.getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void unwrapIpv4MappedLeavesAnOrdinaryIpv6AddressUnchanged() throws Exception {
        var ordinaryV6 = InetAddress.getByAddress(new byte[]{
                (byte) 0xfe, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        });

        var unwrapped = EgressGuard.unwrapIpv4Mapped(ordinaryV6);

        assertThat(unwrapped).isSameAs(ordinaryV6);
    }

    @Test
    void reducedTargetReturnsUnknownForNullUri() {
        assertThat(EgressGuard.reducedTarget(null)).isEqualTo("unknown");
    }

    @Test
    void reducedTargetDropsPathQueryAndUserinfo() {
        assertThat(EgressGuard.reducedTarget(URI.create("https://user:pw@example.com:8443/skills?x=1#f")))
                .isEqualTo("https://example.com:8443");
    }

    @Test
    void ipv4FromReturnsTheFallbackWhenGivenAnInvalidLengthArray() throws Exception {
        var fallback = InetAddress.getByName("203.0.113.5");

        var result = EgressGuard.ipv4From(new byte[]{1, 2, 3}, fallback);

        assertThat(result).isSameAs(fallback);
    }

    @Test
    void literalWrapsUnresolvableLiteralAsExceptionInInitializerError() {
        assertThatThrownBy(() -> EgressGuard.literal("this-does-not-exist-99887766.invalid"))
                .isInstanceOf(ExceptionInInitializerError.class);
    }

    @Test
    void cidrWrapsUnresolvableLiteralAsExceptionInInitializerError() {
        assertThatThrownBy(() -> EgressGuard.cidr("this-does-not-exist-99887766.invalid/24"))
                .isInstanceOf(ExceptionInInitializerError.class);
    }
}
