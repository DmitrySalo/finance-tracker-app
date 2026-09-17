package ru.otus.financetracker.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.recurring.RecurringTransactionOccurrenceRepository;

@Repository
public class JpaRecurringTransactionOccurrenceRepository implements RecurringTransactionOccurrenceRepository {

    private final RecurringTransactionOccurrenceJpaRepository repository;

    public JpaRecurringTransactionOccurrenceRepository(
            RecurringTransactionOccurrenceJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public void save(
            UUID recurringTransactionId,
            LocalDate occurrenceDate,
            UUID transactionId,
            Instant createdAt
    ) {
        repository.saveAndFlush(new RecurringTransactionOccurrenceJpaEntity(
                UUID.randomUUID(),
                recurringTransactionId,
                occurrenceDate,
                transactionId,
                createdAt
        ));
    }
}
