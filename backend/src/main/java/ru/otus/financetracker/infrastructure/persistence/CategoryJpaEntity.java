package ru.otus.financetracker.infrastructure.persistence;

import java.time.Instant;
import java.sql.Types;
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
@Table(name = "categories")
public class CategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 7)
    private TransactionType transactionType;

    @Column(nullable = false, length = 100)
    private String icon;

    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CategoryJpaEntity() {
    }

    CategoryJpaEntity(UUID id, UUID userId, String name, TransactionType transactionType, String icon, String color,
                      Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.transactionType = transactionType;
        this.icon = icon;
        this.color = color;
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

    String getName() {
        return name;
    }

    TransactionType getTransactionType() {
        return transactionType;
    }

    String getIcon() {
        return icon;
    }

    String getColor() {
        return color;
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
