package ru.otus.financetracker.domain.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record Transaction(
        UUID id,
        UUID userId,
        UUID categoryId,
        BigDecimal amount,
        String currency,
        BigDecimal exchangeRateToBase,
        LocalDate transactionDate,
        String description,
        TransactionType transactionType,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
