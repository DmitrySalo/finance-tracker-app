package ru.otus.financetracker.api.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import ru.otus.financetracker.domain.audit.AuditAction;
import ru.otus.financetracker.domain.audit.AuditLogEntry;

public record AuditLogResponse(UUID id, String entityType, UUID entityId, AuditAction action, Instant occurredAt,
                               Map<String, Object> beforeState, Map<String, Object> afterState) {
    static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(entry.id(), entry.entityType(), entry.entityId(), entry.action(), entry.occurredAt(),
                entry.beforeState(), entry.afterState());
    }
}
