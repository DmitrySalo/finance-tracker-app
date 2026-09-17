package ru.otus.financetracker.domain.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record RecurringTransaction(UUID id, UUID userId, UUID categoryId, BigDecimal amount, String currency,
                                   BigDecimal exchangeRateToBase, String description, TransactionType transactionType,
                                   int dayOfMonth, LocalDate startDate, LocalDate nextOccurrenceDate, boolean active,
                                   Instant createdAt, Instant updatedAt, long version) {
}
