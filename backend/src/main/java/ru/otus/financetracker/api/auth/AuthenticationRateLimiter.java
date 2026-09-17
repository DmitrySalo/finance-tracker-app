package ru.otus.financetracker.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import ru.otus.financetracker.configuration.ApplicationProperties;

@Component
public class AuthenticationRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentHashMap<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration windowDuration;

    public AuthenticationRateLimiter(Clock clock, ApplicationProperties properties) {
        this.clock = clock;
        this.maxAttempts = properties.limits().registrationMaxAttempts();
        this.windowDuration = properties.limits().registrationWindow();
    }

    public void check(String clientIpAddress, String normalizedEmail) {
        Instant now = clock.instant();
        if (!tryAcquire("ip:" + clientIpAddress, now) || !tryAcquire("email:" + normalizedEmail, now)) {
            throw new AuthenticationRateLimitExceededException();
        }
    }

    private boolean tryAcquire(String key, Instant now) {
        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(windowDuration)));
            if (windows.size() >= MAX_TRACKED_KEYS) {
                return false;
            }
        }
        return windows.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.startedAt().plus(windowDuration))) {
                return new RateLimitWindow(now, 1);
            }
            return new RateLimitWindow(existing.startedAt(), existing.attempts() + 1);
        }).attempts() <= maxAttempts;
    }

    private record RateLimitWindow(Instant startedAt, int attempts) {
    }
}
