package ru.otus.financetracker.domain.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Budget(
        UUID id,
        UUID userId,
        UUID categoryId,
        LocalDate budgetMonth,
        BigDecimal limitAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
