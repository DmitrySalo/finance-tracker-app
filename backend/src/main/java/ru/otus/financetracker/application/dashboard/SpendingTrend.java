package ru.otus.financetracker.application.dashboard;

import java.time.YearMonth;
import java.util.List;

public record SpendingTrend(YearMonth endMonth, List<DashboardMonthlyExpense> months) {
}
