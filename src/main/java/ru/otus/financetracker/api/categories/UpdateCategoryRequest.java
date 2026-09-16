package ru.otus.financetracker.api.categories;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.otus.financetracker.domain.categories.TransactionType;

public record UpdateCategoryRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
        TransactionType transactionType,
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String icon,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color
) {
}
