package ru.otus.financetracker.application.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DashboardRepository {

    List<DashboardCategoryExpense> findExpenseAmountsByCategory(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            int limit
    );

    List<DashboardCategoryExpense> findTopExpenseAmountsByCategory(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            int limit
    );

    List<DashboardMonthlyExpense> findMonthlyExpenseAmounts(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    );
}
