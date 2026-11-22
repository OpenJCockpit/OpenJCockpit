package nl.metafactory.aicontrol.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitCheckRateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void rejectsCallsOverTheLimit_perUser_andRecoversAfterTheWindow() {
        var clock = new MutableClock();
        var limiter = new GitCheckRateLimiter(2, clock);

        limiter.acquire("alice");
        limiter.acquire("alice");
        assertThatThrownBy(() -> limiter.acquire("alice"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode().value()).isEqualTo(429));
        assertThatCode(() -> limiter.acquire("bob")).doesNotThrowAnyException();

        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.acquire("alice")).doesNotThrowAnyException();
    }
}
