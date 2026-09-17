package ru.otus.financetracker.api.dashboard;

import java.time.YearMonth;
import java.util.List;

import ru.otus.financetracker.application.dashboard.SpendingTrend;

public record SpendingTrendResponse(YearMonth endMonth, List<DashboardMonthlyExpenseResponse> months) {

    static SpendingTrendResponse from(SpendingTrend spendingTrend) {
        return new SpendingTrendResponse(spendingTrend.endMonth(), spendingTrend.months().stream()
                .map(DashboardMonthlyExpenseResponse::from).toList());
    }
}
