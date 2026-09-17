package ru.otus.financetracker.api.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import ru.otus.financetracker.configuration.ApplicationProperties;

class AuthenticationRateLimiterTests {

    @Test
    void shouldAllowNewAuthenticationAfterWindowExpiresAtCapacity() {
        Instant initialTime = Instant.parse("2026-09-16T12:00:00Z");
        var clock = new MutableClock(initialTime);
        var limiter = new AuthenticationRateLimiter(clock, properties());

        for (int key = 0; key < 5_000; key++) {
            limiter.check("192.0.2." + key, "person" + key + "@example.test");
        }

        clock.setInstant(initialTime.plus(Duration.ofMinutes(2)));

        assertThatCode(() -> limiter.check("198.51.100.1", "new@example.test"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAuthenticationAfterMaximumAttempts() {
        var limiter = new AuthenticationRateLimiter(
            Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC),
            properties()
        );

        limiter.check("192.0.2.1", "person@example.test");
        limiter.check("192.0.2.1", "person@example.test");

        assertThatThrownBy(() -> limiter.check("192.0.2.1", "person@example.test"))
            .isInstanceOf(AuthenticationRateLimitExceededException.class);
    }

    private ApplicationProperties properties() {
        return new ApplicationProperties(
                ZoneOffset.UTC,
                new ApplicationProperties.Jwt(
                    "https://issuer.test",
                    "finance-tracker-test",
                    "test-signing-secret-with-at-least-32-characters",
                    Duration.ofMinutes(15)
                ),
                new ApplicationProperties.Cors(List.of("https://frontend.test")),
                new ApplicationProperties.Limits(
                    DataSize.ofMegabytes(1),
                    DataSize.ofKilobytes(512),
                    100,
                    2,
                    Duration.ofMinutes(1)
                )
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
