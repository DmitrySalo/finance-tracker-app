package ru.otus.financetracker.domain.identity;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String baseCurrency,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
