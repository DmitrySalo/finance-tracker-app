package ru.otus.financetracker.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.transactions.TransactionFilter;
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

    @Override
    public Page<Transaction> findAllByUserId(UUID userId, TransactionFilter filter, Pageable pageable) {
        return transactionJpaRepository.findAll(specification(userId, filter), pageable).map(this::toDomain);
    }

    @Override
    public void delete(Transaction transaction) {
        transactionJpaRepository.delete(toEntity(transaction));
        transactionJpaRepository.flush();
    }

    private Specification<TransactionJpaEntity> specification(UUID userId, TransactionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            if (filter.fromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), filter.fromDate()));
            }
            if (filter.toDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), filter.toDate()));
            }
            if (filter.categoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.minAmount() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            if (filter.transactionType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("transactionType"), filter.transactionType()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
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
