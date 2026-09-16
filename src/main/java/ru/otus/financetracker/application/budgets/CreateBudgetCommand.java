package ru.otus.financetracker.application.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBudgetCommand(UUID categoryId, LocalDate budgetMonth, BigDecimal limitAmount, String currency) {
}
