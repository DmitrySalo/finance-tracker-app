package ru.otus.financetracker.domain.categories;

import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID id,
        UUID userId,
        String name,
        TransactionType transactionType,
        String icon,
        String color,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
