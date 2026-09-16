package ru.otus.financetracker.application.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.TransactionType;

public record TransactionFilter(
        LocalDate fromDate,
        LocalDate toDate,
        UUID categoryId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        TransactionType transactionType
) {
}
