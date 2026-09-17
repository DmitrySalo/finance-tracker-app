package ru.otus.financetracker.api.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.domain.transactions.Transaction;

public record TransactionResponse(
        UUID id,
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
    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.categoryId(),
                transaction.amount(),
                transaction.currency(),
                transaction.exchangeRateToBase(),
                transaction.transactionDate(),
                transaction.description(),
                transaction.transactionType(),
                transaction.createdAt(),
                transaction.updatedAt(),
                transaction.version()
        );
    }
}
