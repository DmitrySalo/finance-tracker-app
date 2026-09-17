package ru.otus.financetracker.api.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateBudgetRequest(
        @NotNull @PositiveOrZero Long version,
        UUID categoryId,
        LocalDate budgetMonth,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal limitAmount,
        @Pattern(regexp = "[A-Z]{3}") String currency
) {
}
