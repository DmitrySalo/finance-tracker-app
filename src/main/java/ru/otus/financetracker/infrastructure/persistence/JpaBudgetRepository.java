package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.budgets.BudgetRepository;
import ru.otus.financetracker.domain.budgets.Budget;

@Repository
public class JpaBudgetRepository implements BudgetRepository {
    private final BudgetJpaRepository budgetJpaRepository;
    public JpaBudgetRepository(BudgetJpaRepository budgetJpaRepository) { this.budgetJpaRepository = budgetJpaRepository; }
    @Override public Budget save(Budget budget) {
        budgetJpaRepository.saveAndFlush(toEntity(budget));
        return budgetJpaRepository.findByIdAndUserId(budget.id(), budget.userId()).map(this::toDomain).orElseThrow();
    }
    @Override public Optional<Budget> findByIdAndUserId(UUID budgetId, UUID userId) {
        return budgetJpaRepository.findByIdAndUserId(budgetId, userId).map(this::toDomain);
    }
    @Override public Page<Budget> findAllByUserId(UUID userId, Pageable pageable) {
        return budgetJpaRepository.findAllByUserId(userId, pageable).map(this::toDomain);
    }
    @Override public void delete(Budget budget) { budgetJpaRepository.delete(toEntity(budget)); budgetJpaRepository.flush(); }
    private BudgetJpaEntity toEntity(Budget budget) { return new BudgetJpaEntity(budget.id(), budget.userId(), budget.categoryId(),
            budget.budgetMonth(), budget.limitAmount(), budget.currency(), budget.createdAt(), budget.updatedAt(), budget.version()); }
    private Budget toDomain(BudgetJpaEntity entity) { return new Budget(entity.getId(), entity.getUserId(), entity.getCategoryId(),
            entity.getBudgetMonth(), entity.getLimitAmount(), entity.getCurrency(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()); }
}
