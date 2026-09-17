package ru.otus.financetracker.api.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.domain.recurring.RecurringTransaction;

public record RecurringTransactionResponse(UUID id, UUID categoryId, BigDecimal amount, String currency,
                                           BigDecimal exchangeRateToBase, String description,
                                           TransactionType transactionType, int dayOfMonth, LocalDate startDate,
                                           LocalDate nextOccurrenceDate, boolean active, Instant createdAt,
                                           Instant updatedAt, long version) {
    static RecurringTransactionResponse from(RecurringTransaction rule) {
        return new RecurringTransactionResponse(rule.id(), rule.categoryId(), rule.amount(), rule.currency(),
                rule.exchangeRateToBase(), rule.description(), rule.transactionType(), rule.dayOfMonth(),
                rule.startDate(), rule.nextOccurrenceDate(), rule.active(), rule.createdAt(), rule.updatedAt(), rule.version());
    }
}
