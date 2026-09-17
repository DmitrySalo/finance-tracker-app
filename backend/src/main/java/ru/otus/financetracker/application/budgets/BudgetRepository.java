package ru.otus.financetracker.application.budgets;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.budgets.Budget;

public interface BudgetRepository {

    Budget save(Budget budget);

    Optional<Budget> findByIdAndUserId(UUID budgetId, UUID userId);

    Page<Budget> findAllByUserId(UUID userId, Pageable pageable);

    void delete(Budget budget);
}
