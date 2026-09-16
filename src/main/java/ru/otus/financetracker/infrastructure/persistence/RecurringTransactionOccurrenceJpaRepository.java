package ru.otus.financetracker.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecurringTransactionOccurrenceJpaRepository extends JpaRepository<RecurringTransactionOccurrenceJpaEntity, UUID> {
}
