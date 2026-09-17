package ru.otus.financetracker.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {
    Page<AuditLogJpaEntity> findAllByActorUserIdAndEntityType(UUID actorUserId, String entityType, Pageable pageable);
    Page<AuditLogJpaEntity> findAllByActorUserIdAndEntityTypeAndEntityId(UUID actorUserId, String entityType,
                                                                           UUID entityId, Pageable pageable);
}
