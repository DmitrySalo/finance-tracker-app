package ru.otus.financetracker.api.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateBudgetRequest(
        @NotNull UUID categoryId,
        @NotNull LocalDate budgetMonth,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal limitAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {
}
