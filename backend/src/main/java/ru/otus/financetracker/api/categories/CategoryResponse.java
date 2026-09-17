package ru.otus.financetracker.api.categories;

import java.time.Instant;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;

public record CategoryResponse(
        UUID id,
        String name,
        TransactionType transactionType,
        String icon,
        String color,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.id(),
                category.name(),
                category.transactionType(),
                category.icon(),
                category.color(),
                category.createdAt(),
                category.updatedAt(),
                category.version()
        );
    }
}
