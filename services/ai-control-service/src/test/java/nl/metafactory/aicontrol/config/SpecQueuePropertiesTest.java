package nl.metafactory.aicontrol.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SpecQueuePropertiesTest {

    private static boolean valid(SpecQueueProperties p) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(p).isEmpty();
        }
    }

    @Test
    void defaultsAreValidAndUnconfigured() {
        var p = new SpecQueueProperties();
        assertThat(valid(p)).isTrue();
        assertThat(p.getRunner().isEnabled()).isTrue();
        assertThat(p.getRunner().getPollInterval()).isEqualTo(Duration.ofSeconds(20));
        assertThat(p.getRunner().getMaxQueuedItems()).isEqualTo(500);
        assertThat(p.getRunner().getToken().isConfigured()).isFalse();
    }

    @Test
    void secretLengthIsMeasuredInUtf8Bytes() {
        var p = new SpecQueueProperties();
        p.getRunner().getToken().setSecret("x".repeat(31));
        assertThat(valid(p)).isFalse();
        p.getRunner().getToken().setSecret("x".repeat(32));
        assertThat(valid(p)).isTrue();
        // 16 two-byte characters are 32 bytes although only 16 chars
        p.getRunner().getToken().setSecret("é".repeat(16));
        assertThat(valid(p)).isTrue();
        p.getRunner().getToken().setSecret(null);
        assertThat(valid(p)).isTrue();
        assertThat(p.getRunner().getToken().isConfigured()).isFalse();
    }

    @Test
    void rejectsTooSmallDurationsAndTooLongTtl() {
        var p = new SpecQueueProperties();
        p.getRunner().setPollInterval(Duration.ZERO);
        assertThat(valid(p)).isFalse();
        p = new SpecQueueProperties();
        p.getRunner().getToken().setTtl(Duration.ofSeconds(121));
        assertThat(valid(p)).isFalse();
    }
}
