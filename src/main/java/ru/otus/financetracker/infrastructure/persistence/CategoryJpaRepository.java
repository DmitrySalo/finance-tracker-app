package ru.otus.financetracker.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CategoryJpaEntity> findAllByUserIdOrderByNameAscIdAsc(UUID userId);
}
