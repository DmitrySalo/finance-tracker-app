package ru.otus.financetracker.domain.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
        UUID id,
        UUID actorUserId,
        String entityType,
        UUID entityId,
        AuditAction action,
        Instant occurredAt,
        TransactionAuditState beforeState,
        TransactionAuditState afterState
) {
}
