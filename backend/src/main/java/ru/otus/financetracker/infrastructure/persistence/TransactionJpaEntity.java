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
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate_to_base", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRateToBase;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 7)
    private TransactionType transactionType;

    @Column(name = "recurring_transaction_id")
    private UUID recurringTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TransactionJpaEntity() {
    }

    TransactionJpaEntity(
            UUID id,
            UUID userId,
            UUID categoryId,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRateToBase,
            LocalDate transactionDate,
            String description,
            TransactionType transactionType,
            UUID recurringTransactionId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.currency = currency;
        this.exchangeRateToBase = exchangeRateToBase;
        this.transactionDate = transactionDate;
        this.description = description;
        this.transactionType = transactionType;
        this.recurringTransactionId = recurringTransactionId;
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

    LocalDate getTransactionDate() {
        return transactionDate;
    }

    String getDescription() {
        return description;
    }

    TransactionType getTransactionType() {
        return transactionType;
    }

    UUID getRecurringTransactionId() {
        return recurringTransactionId;
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
