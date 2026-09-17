package ru.otus.financetracker.application.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int TREND_MONTH_COUNT = 6;
    private static final int PIE_CATEGORY_COUNT = 10;
    private static final int TOP_CATEGORY_COUNT = 5;

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Transactional(readOnly = true)
    public Dashboard getDashboard(UUID userId, YearMonth month) {
        LocalDate fromInclusive = month.atDay(1);
        List<DashboardCategoryExpense> categoryExpenses = dashboardRepository.findExpenseAmountsByCategory(
                userId,
                fromInclusive,
                fromInclusive.plusMonths(1),
                PIE_CATEGORY_COUNT
        );
        List<DashboardCategoryExpense> topCategoryExpenses = dashboardRepository.findTopExpenseAmountsByCategory(
                userId,
                fromInclusive,
                fromInclusive.plusMonths(1),
                TOP_CATEGORY_COUNT
        );
        return new Dashboard(month, categoryExpenses, topCategoryExpenses);
    }

    @Transactional(readOnly = true)
    public SpendingTrend getSpendingTrend(UUID userId, YearMonth endMonth) {
        YearMonth startMonth = endMonth.minusMonths(TREND_MONTH_COUNT - 1L);
        Map<LocalDate, BigDecimal> amountsByMonth = dashboardRepository.findMonthlyExpenseAmounts(
                userId,
                startMonth.atDay(1),
                endMonth.plusMonths(1).atDay(1)
        )
                .stream()
                .collect(Collectors.toMap(DashboardMonthlyExpense::month, DashboardMonthlyExpense::amount));
        List<DashboardMonthlyExpense> months = IntStream.range(0, TREND_MONTH_COUNT)
                .mapToObj(startMonth::plusMonths)
                .map(yearMonth -> yearMonth.atDay(1))
                .map(month -> new DashboardMonthlyExpense(
                        month,
                        amountsByMonth.getOrDefault(month, BigDecimal.ZERO)
                ))
                .toList();
        return new SpendingTrend(endMonth, months);
    }
}
