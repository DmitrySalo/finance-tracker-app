package ru.otus.financetracker.api.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.application.budgets.BudgetCalculation;

public record BudgetResponse(UUID id, UUID categoryId, LocalDate budgetMonth, BigDecimal limitAmount, String currency,
                              BigDecimal spentAmount, BigDecimal remainingAmount, BigDecimal percentage,
                              Instant createdAt, Instant updatedAt, long version) {
    static BudgetResponse from(BudgetCalculation calculation) {
        var budget = calculation.budget();
        return new BudgetResponse(budget.id(), budget.categoryId(), budget.budgetMonth(), budget.limitAmount(),
                budget.currency(), calculation.spentAmount(), calculation.remainingAmount(), calculation.percentage(),
                budget.createdAt(), budget.updatedAt(), budget.version());
    }
}
