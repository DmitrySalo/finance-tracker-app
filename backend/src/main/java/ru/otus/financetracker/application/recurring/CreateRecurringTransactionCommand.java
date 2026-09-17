package ru.otus.financetracker.application.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record CreateRecurringTransactionCommand(
        UUID categoryId,
        BigDecimal amount,
        String currency,
        BigDecimal exchangeRateToBase,
        String description,
        TransactionType transactionType,
        int dayOfMonth,
        LocalDate startDate,
        boolean active
) {
}
