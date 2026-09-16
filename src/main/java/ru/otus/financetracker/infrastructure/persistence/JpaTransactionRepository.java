package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.domain.transactions.Transaction;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    public JpaTransactionRepository(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        transactionJpaRepository.saveAndFlush(toEntity(transaction));
        return transactionJpaRepository.findByIdAndUserId(transaction.id(), transaction.userId())
                .map(this::toDomain)
                .orElseThrow();
    }

    @Override
    public Optional<Transaction> findByIdAndUserId(UUID transactionId, UUID userId) {
        return transactionJpaRepository.findByIdAndUserId(transactionId, userId).map(this::toDomain);
    }

    private TransactionJpaEntity toEntity(Transaction transaction) {
        return new TransactionJpaEntity(
                transaction.id(), transaction.userId(), transaction.categoryId(), transaction.amount(), transaction.currency(),
                transaction.exchangeRateToBase(), transaction.transactionDate(), transaction.description(),
                transaction.transactionType(), transaction.createdAt(), transaction.updatedAt(), transaction.version()
        );
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return new Transaction(
                entity.getId(), entity.getUserId(), entity.getCategoryId(), entity.getAmount(), entity.getCurrency(),
                entity.getExchangeRateToBase(), entity.getTransactionDate(), entity.getDescription(), entity.getTransactionType(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()
        );
    }
}
