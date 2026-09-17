package ru.otus.financetracker.application.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.domain.audit.AuditLogEntry;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntry> list(
            UUID actorUserId,
            String entityType,
            UUID entityId,
            Pageable pageable
    ) {
        return auditLogRepository.findAllByActorUserIdAndEntityType(
                actorUserId,
                entityType,
                entityId,
                pageable
        );
    }
}
