package ru.otus.financetracker.domain.audit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record TransactionAuditState(
        UUID categoryId,
        BigDecimal amount,
        String currency,
        BigDecimal exchangeRateToBase,
        LocalDate transactionDate,
        String description,
        TransactionType transactionType
) {
}
