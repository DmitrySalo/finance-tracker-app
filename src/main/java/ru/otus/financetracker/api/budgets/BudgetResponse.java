package ru.otus.financetracker.api.budgets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ru.otus.financetracker.domain.budgets.Budget;

public record BudgetResponse(UUID id, UUID categoryId, LocalDate budgetMonth, BigDecimal limitAmount, String currency,
                             Instant createdAt, Instant updatedAt, long version) {
    static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.id(), budget.categoryId(), budget.budgetMonth(), budget.limitAmount(),
                budget.currency(), budget.createdAt(), budget.updatedAt(), budget.version());
    }
}
