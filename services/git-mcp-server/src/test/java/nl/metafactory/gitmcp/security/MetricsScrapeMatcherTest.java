package nl.metafactory.gitmcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsScrapeMatcherTest {

    private static MockHttpServletRequest request(int localPort, String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setLocalPort(localPort);
        return request;
    }

    @Test
    void withoutASeparateManagementPortPrometheusStaysProtected() {
        assertThat(KeycloakMcpSecurityConfig.metricsScrapeMatcher(new MockEnvironment())).isNull();
        var samePort = new MockEnvironment().withProperty("management.server.port", "8093").withProperty("server.port", "8093");
        assertThat(KeycloakMcpSecurityConfig.metricsScrapeMatcher(samePort)).isNull();
        assertThat(KeycloakMcpSecurityConfig.metricsScrapeMatcher(
                new MockEnvironment().withProperty("management.server.port", "-1"))).isNull();
    }

    @Test
    void onlyPrometheusOnTheManagementPortIsOpen() {
        var matcher = KeycloakMcpSecurityConfig.metricsScrapeMatcher(
                new MockEnvironment().withProperty("management.server.port", "9090").withProperty("server.port", "8093"));

        assertThat(matcher.matches(request(9090, "/actuator/prometheus"))).isTrue();
        assertThat(matcher.matches(request(8093, "/actuator/prometheus"))).isFalse();
        assertThat(matcher.matches(request(9090, "/actuator/env"))).isFalse();
        assertThat(matcher.matches(request(9090, "/sse"))).isFalse();
    }
}
