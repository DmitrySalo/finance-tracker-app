package ru.otus.financetracker.application.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.recurring.RecurringTransaction;

public interface RecurringTransactionRepository {
    RecurringTransaction save(RecurringTransaction recurringTransaction);
    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);
    Page<RecurringTransaction> findAllByUserId(UUID userId, Pageable pageable);
    List<RecurringTransaction> findActiveDueOnOrBefore(LocalDate date);
    void delete(RecurringTransaction recurringTransaction);
}
