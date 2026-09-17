package ru.otus.financetracker.api.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import ru.otus.financetracker.application.dashboard.DashboardMonthlyExpense;

public record DashboardMonthlyExpenseResponse(LocalDate month, BigDecimal amount) {

    static DashboardMonthlyExpenseResponse from(DashboardMonthlyExpense expense) {
        return new DashboardMonthlyExpenseResponse(expense.month(), expense.amount());
    }
}
