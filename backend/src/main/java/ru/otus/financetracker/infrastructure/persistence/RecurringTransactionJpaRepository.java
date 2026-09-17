package ru.otus.financetracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RecurringTransactionJpaRepository extends JpaRepository<RecurringTransactionJpaEntity, UUID> {

    Optional<RecurringTransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<RecurringTransactionJpaEntity> findAllByUserId(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecurringTransactionJpaEntity> findByActiveTrueAndNextOccurrenceDateLessThanEqual(LocalDate date);
}
