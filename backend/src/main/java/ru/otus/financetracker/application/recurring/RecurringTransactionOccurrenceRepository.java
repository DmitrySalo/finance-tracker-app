package ru.otus.financetracker.application.recurring;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface RecurringTransactionOccurrenceRepository {

    void save(UUID recurringTransactionId, LocalDate occurrenceDate, UUID transactionId, Instant createdAt);
}
