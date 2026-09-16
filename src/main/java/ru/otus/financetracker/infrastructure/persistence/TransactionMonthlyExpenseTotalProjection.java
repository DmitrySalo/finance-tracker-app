package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

interface TransactionMonthlyExpenseTotalProjection {

    UUID getCategoryId();

    LocalDate getBudgetMonth();

    BigDecimal getSpentAmount();
}
