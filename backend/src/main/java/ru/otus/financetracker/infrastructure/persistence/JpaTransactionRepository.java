package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.transactions.TransactionExportCursor;
import ru.otus.financetracker.application.transactions.TransactionFilter;
import ru.otus.financetracker.application.transactions.TransactionMonthlyExpenseTotal;
import ru.otus.financetracker.application.transactions.TransactionRepository;
import ru.otus.financetracker.domain.categories.TransactionType;
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
        return transactionJpaRepository.findAll(specification(userId, filter), pageable)
                .map(this::toDomain);
    }

    @Override
    public List<Transaction> findExportChunkByUserId(
            UUID userId,
            TransactionFilter filter,
            TransactionExportCursor cursor,
            int limit
    ) {
        return transactionJpaRepository.findExportChunk(
                        specification(userId, filter),
                        cursor,
                        limit
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumExpenseAmountInBaseCurrency(
            UUID userId,
            UUID categoryId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        return transactionJpaRepository.sumAmountByUserCategoryTypeAndTransactionDateBetween(
                userId, categoryId, TransactionType.EXPENSE, fromInclusive, toExclusive
        );
    }

    @Override
    public List<TransactionMonthlyExpenseTotal> sumExpenseAmountsInBaseCurrencyByMonth(
            UUID userId,
            List<UUID> categoryIds,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return transactionJpaRepository.sumExpenseAmountsByMonth(
                        userId,
                        categoryIds,
                        fromInclusive,
                        toExclusive
                )
                .stream()
                .map(total -> new TransactionMonthlyExpenseTotal(
                        total.getCategoryId(), total.getBudgetMonth(), total.getSpentAmount()
                ))
                .toList();
    }

    @Override
    public void delete(Transaction transaction) {
        transactionJpaRepository.delete(toEntity(transaction));
        transactionJpaRepository.flush();
    }

    private Specification<TransactionJpaEntity> specification(UUID userId, TransactionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            addFilterPredicates(root, criteriaBuilder, predicates, userId, filter);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addFilterPredicates(
            Root<TransactionJpaEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            UUID userId,
            TransactionFilter filter
    ) {
        predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
        if (filter.fromDate() != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("transactionDate"),
                    filter.fromDate()
            ));
        }
        if (filter.toDate() != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("transactionDate"),
                    filter.toDate()
            ));
        }
        if (filter.categoryId() != null) {
            predicates.add(criteriaBuilder.equal(root.get("categoryId"), filter.categoryId()));
        }
        if (filter.minAmount() != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("amount"),
                    filter.minAmount()
            ));
        }
        if (filter.maxAmount() != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("amount"),
                    filter.maxAmount()
            ));
        }
        if (filter.transactionType() != null) {
            predicates.add(criteriaBuilder.equal(root.get("transactionType"), filter.transactionType()));
        }
    }

    private TransactionJpaEntity toEntity(Transaction transaction) {
        return new TransactionJpaEntity(
                transaction.id(),
                transaction.userId(),
                transaction.categoryId(),
                transaction.amount(),
                transaction.currency(),
                transaction.exchangeRateToBase(),
                transaction.transactionDate(),
                transaction.description(),
                transaction.transactionType(),
                transaction.recurringTransactionId(),
                transaction.createdAt(),
                transaction.updatedAt(),
                transaction.version()
        );
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return new Transaction(
                entity.getId(),
                entity.getUserId(),
                entity.getCategoryId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getExchangeRateToBase(),
                entity.getTransactionDate(),
                entity.getDescription(),
                entity.getTransactionType(),
                entity.getRecurringTransactionId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
