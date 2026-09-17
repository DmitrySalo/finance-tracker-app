package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import ru.otus.financetracker.domain.categories.TransactionType;

@Entity
@Table(name = "recurring_transactions")
public class RecurringTransactionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_transaction_type", nullable = false, length = 7)
    private TransactionType categoryTransactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate_to_base", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRateToBase;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 7)
    private TransactionType transactionType;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_occurrence_date", nullable = false)
    private LocalDate nextOccurrenceDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected RecurringTransactionJpaEntity() {
    }

    RecurringTransactionJpaEntity(
            UUID id,
            UUID userId,
            UUID categoryId,
            TransactionType categoryTransactionType,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRateToBase,
            String description,
            TransactionType transactionType,
            int dayOfMonth,
            LocalDate startDate,
            LocalDate nextOccurrenceDate,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.categoryTransactionType = categoryTransactionType;
        this.amount = amount;
        this.currency = currency;
        this.exchangeRateToBase = exchangeRateToBase;
        this.description = description;
        this.transactionType = transactionType;
        this.dayOfMonth = dayOfMonth;
        this.startDate = startDate;
        this.nextOccurrenceDate = nextOccurrenceDate;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getExchangeRateToBase() {
        return exchangeRateToBase;
    }

    String getDescription() {
        return description;
    }

    TransactionType getTransactionType() {
        return transactionType;
    }

    int getDayOfMonth() {
        return dayOfMonth;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getNextOccurrenceDate() {
        return nextOccurrenceDate;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    long getVersion() {
        return version;
    }
}
