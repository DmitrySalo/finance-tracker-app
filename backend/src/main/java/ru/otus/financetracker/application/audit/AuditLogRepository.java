package ru.otus.financetracker.application.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.audit.AuditLog;
import ru.otus.financetracker.domain.audit.AuditLogEntry;

public interface AuditLogRepository {

    void save(AuditLog auditLog);

    Page<AuditLogEntry> findAllByActorUserIdAndEntityType(UUID actorUserId, String entityType, UUID entityId,
                                                           Pageable pageable);
}
