package ru.otus.financetracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface DashboardJpaRepository extends Repository<TransactionJpaEntity, UUID> {

    @Query(value = """
            SELECT transaction.category_id AS categoryId,
                   category.name AS categoryName,
                   category.icon AS categoryIcon,
                   category.color AS categoryColor,
                   SUM(transaction.amount * transaction.exchange_rate_to_base) AS amount
            FROM transactions transaction
            JOIN categories category ON category.id = transaction.category_id
            WHERE transaction.user_id = :userId
              AND transaction.transaction_type = 'EXPENSE'
              AND transaction.transaction_date >= :fromInclusive
              AND transaction.transaction_date < :toExclusive
            GROUP BY transaction.category_id, category.name, category.icon, category.color
            ORDER BY amount DESC, transaction.category_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardCategoryExpenseProjection> findExpenseAmountsByCategory(
            @Param("userId") UUID userId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT transaction.category_id AS categoryId,
                   category.name AS categoryName,
                   category.icon AS categoryIcon,
                   category.color AS categoryColor,
                   SUM(transaction.amount * transaction.exchange_rate_to_base) AS amount
            FROM transactions transaction
            JOIN categories category ON category.id = transaction.category_id
            WHERE transaction.user_id = :userId
              AND transaction.transaction_type = 'EXPENSE'
              AND transaction.transaction_date >= :fromInclusive
              AND transaction.transaction_date < :toExclusive
            GROUP BY transaction.category_id, category.name, category.icon, category.color
            ORDER BY amount DESC, transaction.category_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardCategoryExpenseProjection> findTopExpenseAmountsByCategory(
            @Param("userId") UUID userId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT date_trunc('month', transaction_date)::date AS month,
                   SUM(amount * exchange_rate_to_base) AS amount
            FROM transactions
            WHERE user_id = :userId
              AND transaction_type = 'EXPENSE'
              AND transaction_date >= :fromInclusive
              AND transaction_date < :toExclusive
            GROUP BY date_trunc('month', transaction_date)::date
            ORDER BY month ASC
            """, nativeQuery = true)
    List<DashboardMonthlyExpenseProjection> findMonthlyExpenseAmounts(
            @Param("userId") UUID userId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive
    );
}
