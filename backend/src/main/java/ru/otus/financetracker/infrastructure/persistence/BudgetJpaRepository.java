package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BudgetJpaRepository extends JpaRepository<BudgetJpaEntity, UUID> {
    Optional<BudgetJpaEntity> findByIdAndUserId(UUID id, UUID userId);
    Page<BudgetJpaEntity> findAllByUserId(UUID userId, Pageable pageable);
}
