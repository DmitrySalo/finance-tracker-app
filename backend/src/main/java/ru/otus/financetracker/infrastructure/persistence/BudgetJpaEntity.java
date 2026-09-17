package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "budgets")
public class BudgetJpaEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(name = "budget_month", nullable = false) private LocalDate budgetMonth;
    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 4) private BigDecimal limitAmount;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected BudgetJpaEntity() { }

    BudgetJpaEntity(UUID id, UUID userId, UUID categoryId, LocalDate budgetMonth, BigDecimal limitAmount, String currency,
                    Instant createdAt, Instant updatedAt, long version) {
        this.id = id; this.userId = userId; this.categoryId = categoryId; this.budgetMonth = budgetMonth;
        this.limitAmount = limitAmount; this.currency = currency; this.createdAt = createdAt; this.updatedAt = updatedAt;
        this.version = version;
    }
    UUID getId() { return id; } UUID getUserId() { return userId; } UUID getCategoryId() { return categoryId; }
    LocalDate getBudgetMonth() { return budgetMonth; } BigDecimal getLimitAmount() { return limitAmount; }
    String getCurrency() { return currency; } Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; } long getVersion() { return version; }
}
