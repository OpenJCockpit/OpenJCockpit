package nl.metafactory.common.security;

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
        assertThat(ResourceServerSecurityConfig.metricsScrapeMatcher(new MockEnvironment())).isNull();
        var samePort = new MockEnvironment().withProperty("management.server.port", "8080").withProperty("server.port", "8080");
        assertThat(ResourceServerSecurityConfig.metricsScrapeMatcher(samePort)).isNull();
        assertThat(ResourceServerSecurityConfig.metricsScrapeMatcher(
                new MockEnvironment().withProperty("management.server.port", "-1"))).isNull();
    }

    @Test
    void onlyPrometheusOnTheManagementPortIsOpen() {
        var matcher = ResourceServerSecurityConfig.metricsScrapeMatcher(
                new MockEnvironment().withProperty("management.server.port", "9090").withProperty("server.port", "8080"));

        assertThat(matcher.matches(request(9090, "/actuator/prometheus"))).isTrue();
        assertThat(matcher.matches(request(8080, "/actuator/prometheus"))).isFalse();
        assertThat(matcher.matches(request(9090, "/actuator/env"))).isFalse();
        assertThat(matcher.matches(request(9090, "/api/projects"))).isFalse();
    }
}
