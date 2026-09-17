package ru.otus.financetracker.application.budgets;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.application.audit.BudgetAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.application.identity.UserAuthenticationRepository;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.domain.budgets.Budget;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserAuthenticationRepository userAuthenticationRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetAuditService budgetAuditService;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
                         UserAuthenticationRepository userAuthenticationRepository,
                         TransactionRepository transactionRepository, BudgetAuditService budgetAuditService, Clock clock) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.userAuthenticationRepository = userAuthenticationRepository;
        this.transactionRepository = transactionRepository;
        this.budgetAuditService = budgetAuditService;
        this.clock = clock;
    }

    @Transactional
    public BudgetCalculation create(UUID userId, CreateBudgetCommand command) {
        validateBudgetMonth(command.budgetMonth());
        requireExpenseCategory(userId, command.categoryId());
        requireBaseCurrency(userId, command.currency());
        Instant now = clock.instant();
        Budget budget = budgetRepository.save(new Budget(UUID.randomUUID(), userId, command.categoryId(), command.budgetMonth(),
                command.limitAmount(), command.currency(), now, now, 0));
        budgetAuditService.recordCreate(userId, budget);
        return calculate(budget);
    }

    @Transactional(readOnly = true)
    public BudgetCalculation get(UUID userId, UUID budgetId) {
        return calculate(findOwnedBudget(userId, budgetId));
    }

    @Transactional(readOnly = true)
    public Page<BudgetCalculation> list(UUID userId, Pageable pageable) {
        Page<Budget> budgets = budgetRepository.findAllByUserId(userId, pageable);
        Map<BudgetPeriod, BigDecimal> spentAmounts = calculateSpentAmounts(budgets.getContent());
        return budgets.map(budget -> BudgetCalculation.from(budget,
                spentAmounts.getOrDefault(new BudgetPeriod(budget.categoryId(), budget.budgetMonth()), BigDecimal.ZERO)));
    }

    @Transactional
    public BudgetCalculation update(UUID userId, UUID budgetId, UpdateBudgetCommand command) {
        Budget budget = findOwnedBudget(userId, budgetId);
        if (budget.version() != command.version()) {
            throw new OptimisticLockingFailureException("Budget has been modified.");
        }
        UUID categoryId = command.categoryId() == null ? budget.categoryId() : command.categoryId();
        requireExpenseCategory(userId, categoryId);
        var budgetMonth = command.budgetMonth() == null ? budget.budgetMonth() : command.budgetMonth();
        validateBudgetMonth(budgetMonth);
        String currency = command.currency() == null ? budget.currency() : command.currency();
        requireBaseCurrency(userId, currency);
        Budget updated = budgetRepository.save(new Budget(budget.id(), budget.userId(), categoryId, budgetMonth,
                command.limitAmount() == null ? budget.limitAmount() : command.limitAmount(),
                currency, budget.createdAt(), clock.instant(),
                budget.version()));
        budgetAuditService.recordUpdate(userId, budget, updated);
        return calculate(updated);
    }

    @Transactional
    public void delete(UUID userId, UUID budgetId, long version) {
        Budget budget = findOwnedBudget(userId, budgetId);
        if (budget.version() != version) {
            throw new OptimisticLockingFailureException("Budget has been modified.");
        }
        budgetAuditService.recordDelete(userId, budget);
        budgetRepository.delete(budget);
    }

    private void validateBudgetMonth(java.time.LocalDate budgetMonth) {
        if (budgetMonth.getDayOfMonth() != 1) {
            throw new BudgetMonthMustBeFirstDayException();
        }
    }

    private void requireExpenseCategory(UUID userId, UUID categoryId) {
        var category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (category.transactionType() != TransactionType.EXPENSE) {
            throw new BudgetCategoryMustBeExpenseException();
        }
    }

    private Budget findOwnedBudget(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId).orElseThrow(ResourceNotFoundException::new);
    }

    private void requireBaseCurrency(UUID userId, String currency) {
        boolean matchesBaseCurrency = userAuthenticationRepository.findById(userId)
                .map(user -> user.baseCurrency().equals(currency))
                .orElse(false);
        if (!matchesBaseCurrency) {
            throw new BudgetCurrencyMustMatchUserBaseCurrencyException();
        }
    }

    private BudgetCalculation calculate(Budget budget) {
        var monthEnd = budget.budgetMonth().plusMonths(1);
        var spentAmount = transactionRepository.sumExpenseAmountInBaseCurrency(
                budget.userId(), budget.categoryId(), budget.budgetMonth(), monthEnd
        );
        return BudgetCalculation.from(budget, spentAmount);
    }

    private Map<BudgetPeriod, BigDecimal> calculateSpentAmounts(List<Budget> budgets) {
        if (budgets.isEmpty()) {
            return Map.of();
        }
        return budgets.stream()
                .collect(Collectors.groupingBy(Budget::budgetMonth))
                .entrySet().stream()
                .flatMap(entry -> transactionRepository.sumExpenseAmountsInBaseCurrencyByMonth(
                                budgets.getFirst().userId(), entry.getValue().stream().map(Budget::categoryId).distinct().toList(),
                                entry.getKey(), entry.getKey().plusMonths(1))
                        .stream())
                .collect(Collectors.toMap(total -> new BudgetPeriod(total.categoryId(), total.budgetMonth()),
                        total -> total.spentAmount()));
    }

    private record BudgetPeriod(UUID categoryId, java.time.LocalDate budgetMonth) {
    }
}
