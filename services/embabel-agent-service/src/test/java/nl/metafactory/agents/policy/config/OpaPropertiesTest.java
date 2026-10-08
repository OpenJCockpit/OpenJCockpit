package nl.metafactory.agents.policy.config;

import nl.metafactory.agents.policy.model.FailMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaPropertiesTest {

    @Test
    void defaultsAreFailClosedAndDisabled() {
        var properties = new OpaProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getFailMode()).isEqualTo(FailMode.FAIL_CLOSED);
        assertThat(properties.getPolicyPath()).isEqualTo("/v1/data/metafactory/workflow/decision");
        assertThat(properties.getHealthPath()).isEqualTo("/health");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(3);
        assertThat(properties.isDecisionLoggingEnabled()).isTrue();
        assertThat(properties.isDecisionLogExportEnabled()).isTrue();
    }

    @Test
    void allFieldsCanBeSetAndRead() {
        var properties = new OpaProperties();

        properties.setEnabled(true);
        properties.setBaseUrl("http://opa:8181");
        properties.setPolicyPath("/v1/data/custom/decision");
        properties.setHealthPath("/healthz");
        properties.setTimeoutSeconds(5);
        properties.setFailMode(FailMode.FAIL_OPEN);
        properties.setDecisionLoggingEnabled(false);
        properties.setDecisionLogExportEnabled(false);
        properties.setEnvironment("prod");
        properties.setCustomerLabel("Noordzee Logistics");
        properties.setProjectLabel("public-sector-case-system");
        properties.setAuthToken("secret-token");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("http://opa:8181");
        assertThat(properties.getPolicyPath()).isEqualTo("/v1/data/custom/decision");
        assertThat(properties.getHealthPath()).isEqualTo("/healthz");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(5);
        assertThat(properties.getFailMode()).isEqualTo(FailMode.FAIL_OPEN);
        assertThat(properties.isDecisionLoggingEnabled()).isFalse();
        assertThat(properties.isDecisionLogExportEnabled()).isFalse();
        assertThat(properties.getEnvironment()).isEqualTo("prod");
        assertThat(properties.getCustomerLabel()).isEqualTo("Noordzee Logistics");
        assertThat(properties.getProjectLabel()).isEqualTo("public-sector-case-system");
        assertThat(properties.getAuthToken()).isEqualTo("secret-token");
    }
}
