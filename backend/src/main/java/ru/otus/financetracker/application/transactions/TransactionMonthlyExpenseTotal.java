package ru.otus.financetracker.application.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionMonthlyExpenseTotal(UUID categoryId, LocalDate budgetMonth, BigDecimal spentAmount) {
}
