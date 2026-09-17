package ru.otus.financetracker.application.dashboard;

import java.time.YearMonth;
import java.util.List;

public record Dashboard(YearMonth month, List<DashboardCategoryExpense> expensesByCategory,
                        List<DashboardCategoryExpense> topExpenseCategories) {
}
