package ru.otus.financetracker.api.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

import ru.otus.financetracker.application.dashboard.DashboardCategoryExpense;

public record DashboardCategoryExpenseResponse(
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        BigDecimal amount
) {

    static DashboardCategoryExpenseResponse from(DashboardCategoryExpense expense) {
        return new DashboardCategoryExpenseResponse(
                expense.categoryId(),
                expense.categoryName(),
                expense.categoryIcon(),
                expense.categoryColor(),
                expense.amount()
        );
    }
}
