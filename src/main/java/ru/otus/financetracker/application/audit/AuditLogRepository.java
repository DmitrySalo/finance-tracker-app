package ru.otus.financetracker.application.audit;

import ru.otus.financetracker.domain.audit.AuditLog;

public interface AuditLogRepository {

    void save(AuditLog auditLog);
}
