package ru.otus.financetracker.api.dashboard;

import java.time.YearMonth;
import java.util.List;

import ru.otus.financetracker.application.dashboard.Dashboard;

public record DashboardResponse(
        YearMonth month,
        List<DashboardCategoryExpenseResponse> expensesByCategory,
        List<DashboardCategoryExpenseResponse> topExpenseCategories
) {

    static DashboardResponse from(Dashboard dashboard) {
        return new DashboardResponse(
                dashboard.month(),
                dashboard.expensesByCategory().stream()
                        .map(DashboardCategoryExpenseResponse::from)
                        .toList(),
                dashboard.topExpenseCategories().stream()
                        .map(DashboardCategoryExpenseResponse::from)
                        .toList()
        );
    }
}
