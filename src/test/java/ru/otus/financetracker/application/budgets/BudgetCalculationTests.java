package ru.otus.financetracker.application.budgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ru.otus.financetracker.domain.budgets.Budget;

class BudgetCalculationTests {

    @Test
    void shouldCalculateZeroPercentWhenThereAreNoExpenses() {
        var calculation = BudgetCalculation.from(budget("100.0000"), BigDecimal.ZERO);

        assertThat(calculation.spentAmount()).isEqualByComparingTo("0");
        assertThat(calculation.remainingAmount()).isEqualByComparingTo("100.0000");
        assertThat(calculation.percentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCalculateOneHundredPercentAtBudgetLimit() {
        var calculation = BudgetCalculation.from(budget("100.0000"), new BigDecimal("100.0000"));

        assertThat(calculation.remainingAmount()).isEqualByComparingTo("0.0000");
        assertThat(calculation.percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldCalculateNegativeRemainingAndPercentageAboveOneHundredWhenBudgetIsExceeded() {
        var calculation = BudgetCalculation.from(budget("100.0000"), new BigDecimal("125.5000"));

        assertThat(calculation.remainingAmount()).isEqualByComparingTo("-25.5000");
        assertThat(calculation.percentage()).isEqualByComparingTo("125.50");
    }

    private Budget budget(String limitAmount) {
        Instant timestamp = Instant.parse("2026-09-16T12:00:00Z");
        return new Budget(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1),
                new BigDecimal(limitAmount), "USD", timestamp, timestamp, 0);
    }
}
