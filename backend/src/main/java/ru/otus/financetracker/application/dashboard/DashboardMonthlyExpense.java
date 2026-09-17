package ru.otus.financetracker.application.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardMonthlyExpense(LocalDate month, BigDecimal amount) {
}
