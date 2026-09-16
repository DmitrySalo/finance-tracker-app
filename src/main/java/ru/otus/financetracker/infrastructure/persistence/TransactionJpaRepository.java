package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.otus.financetracker.domain.categories.TransactionType;

interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID>, JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT COALESCE(SUM(transaction.amount * transaction.exchangeRateToBase), 0)
            FROM TransactionJpaEntity transaction
            WHERE transaction.userId = :userId
              AND transaction.categoryId = :categoryId
              AND transaction.transactionType = :transactionType
              AND transaction.transactionDate >= :fromInclusive
              AND transaction.transactionDate < :toExclusive
            """)
    BigDecimal sumAmountByUserCategoryTypeAndTransactionDateBetween(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("transactionType") TransactionType transactionType,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive
    );

    @Query(value = """
            SELECT category_id AS categoryId,
                   date_trunc('month', transaction_date)::date AS budgetMonth,
                   SUM(amount * exchange_rate_to_base) AS spentAmount
            FROM transactions
            WHERE user_id = :userId
              AND category_id IN (:categoryIds)
              AND transaction_type = 'EXPENSE'
              AND transaction_date >= :fromInclusive
              AND transaction_date < :toExclusive
            GROUP BY category_id, date_trunc('month', transaction_date)::date
            """, nativeQuery = true)
    List<TransactionMonthlyExpenseTotalProjection> sumExpenseAmountsByMonth(
            @Param("userId") UUID userId,
            @Param("categoryIds") List<UUID> categoryIds,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive
    );
}
