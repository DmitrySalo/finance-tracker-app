package ru.otus.financetracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.dashboard.DashboardCategoryExpense;
import ru.otus.financetracker.application.dashboard.DashboardMonthlyExpense;
import ru.otus.financetracker.application.dashboard.DashboardRepository;

@Repository
public class JpaDashboardRepository implements DashboardRepository {

    private final DashboardJpaRepository dashboardJpaRepository;

    public JpaDashboardRepository(DashboardJpaRepository dashboardJpaRepository) {
        this.dashboardJpaRepository = dashboardJpaRepository;
    }

    @Override
    public List<DashboardCategoryExpense> findExpenseAmountsByCategory(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            int limit
    ) {
        return dashboardJpaRepository.findExpenseAmountsByCategory(
                        userId,
                        fromInclusive,
                        toExclusive,
                        limit
                )
                .stream()
                .map(expense -> new DashboardCategoryExpense(
                        expense.getCategoryId(),
                        expense.getCategoryName(),
                        expense.getCategoryIcon(),
                        expense.getCategoryColor(),
                        expense.getAmount()
                ))
                .toList();
    }

    @Override
    public List<DashboardCategoryExpense> findTopExpenseAmountsByCategory(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            int limit
    ) {
        return dashboardJpaRepository.findTopExpenseAmountsByCategory(
                        userId,
                        fromInclusive,
                        toExclusive,
                        limit
                )
                .stream()
                .map(expense -> new DashboardCategoryExpense(
                        expense.getCategoryId(),
                        expense.getCategoryName(),
                        expense.getCategoryIcon(),
                        expense.getCategoryColor(),
                        expense.getAmount()
                ))
                .toList();
    }

    @Override
    public List<DashboardMonthlyExpense> findMonthlyExpenseAmounts(
            UUID userId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        return dashboardJpaRepository.findMonthlyExpenseAmounts(
                        userId,
                        fromInclusive,
                        toExclusive
                )
                .stream()
                .map(expense -> new DashboardMonthlyExpense(expense.getMonth(), expense.getAmount()))
                .toList();
    }
}
