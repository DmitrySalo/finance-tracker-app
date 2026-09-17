package ru.otus.financetracker.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.otus.financetracker.domain.audit.AuditAction;

@Entity
@Table(name = "audit_logs")
public class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private JsonNode beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private JsonNode afterState;

    protected AuditLogJpaEntity() {
    }

    AuditLogJpaEntity(
            UUID id,
            UUID actorUserId,
            String entityType,
            UUID entityId,
            AuditAction action,
            Instant occurredAt,
            JsonNode beforeState,
            JsonNode afterState
    ) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.occurredAt = occurredAt;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }

    UUID id() {
        return id;
    }

    String entityType() {
        return entityType;
    }

    UUID entityId() {
        return entityId;
    }

    AuditAction action() {
        return action;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    JsonNode beforeState() {
        return beforeState;
    }

    JsonNode afterState() {
        return afterState;
    }
}
