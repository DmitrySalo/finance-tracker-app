package ru.otus.financetracker.api.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.otus.financetracker.domain.categories.TransactionType;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull TransactionType transactionType,
        @NotBlank @Size(max = 100) String icon,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color
) {
}
