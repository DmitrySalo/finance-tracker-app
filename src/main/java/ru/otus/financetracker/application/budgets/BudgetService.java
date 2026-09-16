package ru.otus.financetracker.application.budgets;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.application.audit.BudgetAuditService;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.budgets.Budget;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetAuditService budgetAuditService;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
                         BudgetAuditService budgetAuditService, Clock clock) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.budgetAuditService = budgetAuditService;
        this.clock = clock;
    }

    @Transactional
    public Budget create(UUID userId, CreateBudgetCommand command) {
        validateBudgetMonth(command.budgetMonth());
        requireExpenseCategory(userId, command.categoryId());
        Instant now = clock.instant();
        Budget budget = budgetRepository.save(new Budget(UUID.randomUUID(), userId, command.categoryId(), command.budgetMonth(),
                command.limitAmount(), command.currency(), now, now, 0));
        budgetAuditService.recordCreate(userId, budget);
        return budget;
    }

    @Transactional(readOnly = true)
    public Budget get(UUID userId, UUID budgetId) {
        return findOwnedBudget(userId, budgetId);
    }

    @Transactional(readOnly = true)
    public Page<Budget> list(UUID userId, Pageable pageable) {
        return budgetRepository.findAllByUserId(userId, pageable);
    }

    @Transactional
    public Budget update(UUID userId, UUID budgetId, UpdateBudgetCommand command) {
        Budget budget = findOwnedBudget(userId, budgetId);
        if (budget.version() != command.version()) {
            throw new OptimisticLockingFailureException("Budget has been modified.");
        }
        UUID categoryId = command.categoryId() == null ? budget.categoryId() : command.categoryId();
        requireExpenseCategory(userId, categoryId);
        var budgetMonth = command.budgetMonth() == null ? budget.budgetMonth() : command.budgetMonth();
        validateBudgetMonth(budgetMonth);
        Budget updated = budgetRepository.save(new Budget(budget.id(), budget.userId(), categoryId, budgetMonth,
                command.limitAmount() == null ? budget.limitAmount() : command.limitAmount(),
                command.currency() == null ? budget.currency() : command.currency(), budget.createdAt(), clock.instant(),
                budget.version()));
        budgetAuditService.recordUpdate(userId, budget, updated);
        return updated;
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
}
