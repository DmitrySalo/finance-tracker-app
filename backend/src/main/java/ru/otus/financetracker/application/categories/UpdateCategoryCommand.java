package ru.otus.financetracker.application.categories;

import ru.otus.financetracker.domain.categories.TransactionType;

public record UpdateCategoryCommand(
        long version,
        String name,
        TransactionType transactionType,
        String icon,
        String color
) {
}
