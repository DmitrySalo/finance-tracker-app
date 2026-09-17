package ru.otus.financetracker.application.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record UpdateTransactionCommand(
        long version,
        UUID categoryId,
        BigDecimal amount,
        String currency,
        BigDecimal exchangeRateToBase,
        LocalDate transactionDate,
        String description,
        TransactionType transactionType
) {
}
