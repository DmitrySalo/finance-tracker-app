package ru.otus.financetracker.domain.audit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetAuditState(
        UUID categoryId,
        LocalDate budgetMonth,
        BigDecimal limitAmount,
        String currency
) implements AuditState {
}
