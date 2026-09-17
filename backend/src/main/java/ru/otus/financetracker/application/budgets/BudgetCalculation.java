package ru.otus.financetracker.application.budgets;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ru.otus.financetracker.domain.budgets.Budget;

public record BudgetCalculation(
        Budget budget,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        BigDecimal percentage
) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 4;

    public static BudgetCalculation from(Budget budget, BigDecimal spentAmount) {
        BigDecimal roundedSpentAmount = spentAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = budget.limitAmount()
                .subtract(roundedSpentAmount)
                .setScale(MONEY_SCALE);
        BigDecimal percentage = roundedSpentAmount.multiply(ONE_HUNDRED)
                .divide(budget.limitAmount(), 2, RoundingMode.HALF_UP);
        return new BudgetCalculation(budget, roundedSpentAmount, remainingAmount, percentage);
    }
}
