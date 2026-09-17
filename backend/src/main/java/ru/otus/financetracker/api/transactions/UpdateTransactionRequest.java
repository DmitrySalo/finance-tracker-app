package ru.otus.financetracker.api.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ru.otus.financetracker.domain.categories.TransactionType;

public record UpdateTransactionRequest(
        @NotNull @PositiveOrZero Long version,
        UUID categoryId,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @Positive @Digits(integer = 11, fraction = 8) BigDecimal exchangeRateToBase,
        LocalDate transactionDate,
        @Size(max = 1000) String description,
        TransactionType transactionType
) {
}
