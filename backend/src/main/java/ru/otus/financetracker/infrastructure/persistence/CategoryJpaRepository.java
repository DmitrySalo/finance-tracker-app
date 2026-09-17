package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<CategoryJpaEntity> findAllByUserId(UUID userId, Pageable pageable);
}
