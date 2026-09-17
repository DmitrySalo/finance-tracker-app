package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

interface DashboardMonthlyExpenseProjection {

    LocalDate getMonth();

    BigDecimal getAmount();
}
