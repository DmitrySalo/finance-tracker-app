package ru.otus.financetracker.api.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ru.otus.financetracker.domain.categories.TransactionType;

public record CreateTransactionRequest(
        @NotNull UUID categoryId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal exchangeRateToBase,
        @NotNull LocalDate transactionDate,
        @Size(max = 1000) String description,
        @NotNull TransactionType transactionType
) {
}
