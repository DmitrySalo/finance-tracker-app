package ru.otus.financetracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.recurring.RecurringTransactionRepository;
import ru.otus.financetracker.domain.recurring.RecurringTransaction;

@Repository
public class JpaRecurringTransactionRepository implements RecurringTransactionRepository {
    private final RecurringTransactionJpaRepository repository;
    public JpaRecurringTransactionRepository(RecurringTransactionJpaRepository repository) { this.repository = repository; }
    @Override public RecurringTransaction save(RecurringTransaction rule) {
        repository.saveAndFlush(toEntity(rule));
        return repository.findByIdAndUserId(rule.id(), rule.userId()).map(this::toDomain).orElseThrow();
    }
    @Override public Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId) { return repository.findByIdAndUserId(id, userId).map(this::toDomain); }
    @Override public Page<RecurringTransaction> findAllByUserId(UUID userId, Pageable pageable) { return repository.findAllByUserId(userId, pageable).map(this::toDomain); }
    @Override public List<RecurringTransaction> findActiveDueOnOrBefore(LocalDate date) { return repository.findByActiveTrueAndNextOccurrenceDateLessThanEqual(date).stream().map(this::toDomain).toList(); }
    @Override public void delete(RecurringTransaction rule) { repository.delete(toEntity(rule)); repository.flush(); }
    private RecurringTransactionJpaEntity toEntity(RecurringTransaction rule) { return new RecurringTransactionJpaEntity(rule.id(), rule.userId(), rule.categoryId(), rule.transactionType(), rule.amount(), rule.currency(), rule.exchangeRateToBase(), rule.description(), rule.transactionType(), rule.dayOfMonth(), rule.startDate(), rule.nextOccurrenceDate(), rule.active(), rule.createdAt(), rule.updatedAt(), rule.version()); }
    private RecurringTransaction toDomain(RecurringTransactionJpaEntity entity) { return new RecurringTransaction(entity.getId(), entity.getUserId(), entity.getCategoryId(), entity.getAmount(), entity.getCurrency(), entity.getExchangeRateToBase(), entity.getDescription(), entity.getTransactionType(), entity.getDayOfMonth(), entity.getStartDate(), entity.getNextOccurrenceDate(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()); }
}
