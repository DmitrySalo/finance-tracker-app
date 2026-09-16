package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID>, JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);
}
