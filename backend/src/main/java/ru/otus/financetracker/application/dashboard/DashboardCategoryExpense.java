package ru.otus.financetracker.application.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

public record DashboardCategoryExpense(
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        BigDecimal amount
) {
}
