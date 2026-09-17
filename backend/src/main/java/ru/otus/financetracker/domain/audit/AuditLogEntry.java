package ru.otus.financetracker.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogEntry(UUID id, String entityType, UUID entityId, AuditAction action, Instant occurredAt,
                            Map<String, Object> beforeState, Map<String, Object> afterState) {
}
