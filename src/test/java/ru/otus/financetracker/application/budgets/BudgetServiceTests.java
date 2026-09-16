package ru.otus.financetracker.application.budgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ru.otus.financetracker.application.audit.BudgetAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

class BudgetServiceTests {
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private final BudgetService service = new BudgetService(budgetRepository, categoryRepository,
            mock(BudgetAuditService.class), clock);

    @Test
    void shouldCreateBudgetForOwnedExpenseCategoryAndFirstDayOfMonth() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category(userId, categoryId, TransactionType.EXPENSE)));
        when(budgetRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var budget = service.create(userId, new CreateBudgetCommand(categoryId, LocalDate.of(2026, 9, 1),
                new BigDecimal("500.0000"), "EUR"));

        assertThat(budget.budgetMonth()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(budget.limitAmount()).isEqualByComparingTo("500.0000");
        assertThat(budget.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldRejectBudgetForIncomeCategory() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category(userId, categoryId, TransactionType.INCOME)));

        assertThatThrownBy(() -> service.create(userId, command(categoryId, LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BudgetCategoryMustBeExpenseException.class);
    }

    @Test
    void shouldRejectBudgetMonthThatIsNotFirstDay() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), command(UUID.randomUUID(), LocalDate.of(2026, 9, 2))))
                .isInstanceOf(BudgetMonthMustBeFirstDayException.class);
    }

    @Test
    void shouldHideBudgetOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        when(budgetRepository.findByIdAndUserId(budgetId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, budgetId)).isInstanceOf(ResourceNotFoundException.class);
    }

    private CreateBudgetCommand command(UUID categoryId, LocalDate budgetMonth) {
        return new CreateBudgetCommand(categoryId, budgetMonth, new BigDecimal("500.0000"), "EUR");
    }

    private Category category(UUID userId, UUID categoryId, TransactionType type) {
        return new Category(categoryId, userId, "Food", type, "utensils", "#0A1B2C", clock.instant(), clock.instant(), 0);
    }
}
