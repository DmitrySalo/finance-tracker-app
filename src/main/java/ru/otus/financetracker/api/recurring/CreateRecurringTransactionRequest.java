package ru.otus.financetracker.api.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ru.otus.financetracker.domain.categories.TransactionType;

public record CreateRecurringTransactionRequest(@NotNull UUID categoryId,
                                               @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
                                               @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                                               @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal exchangeRateToBase,
                                               @Size(max = 1000) String description, @NotNull TransactionType transactionType,
                                               @Min(1) @Max(31) int dayOfMonth, @NotNull LocalDate startDate,
                                               boolean active) {
}
