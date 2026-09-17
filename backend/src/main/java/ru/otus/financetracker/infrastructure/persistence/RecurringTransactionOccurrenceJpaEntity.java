package ru.otus.financetracker.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_transaction_occurrences")
public class RecurringTransactionOccurrenceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "recurring_transaction_id", nullable = false)
    private UUID recurringTransactionId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecurringTransactionOccurrenceJpaEntity() {
    }

    RecurringTransactionOccurrenceJpaEntity(
            UUID id,
            UUID recurringTransactionId,
            LocalDate occurrenceDate,
            UUID transactionId,
            Instant createdAt
    ) {
        this.id = id;
        this.recurringTransactionId = recurringTransactionId;
        this.occurrenceDate = occurrenceDate;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }
}
