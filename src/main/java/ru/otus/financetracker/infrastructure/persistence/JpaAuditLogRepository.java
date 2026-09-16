package ru.otus.financetracker.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.otus.financetracker.application.audit.AuditLogRepository;
import ru.otus.financetracker.domain.audit.AuditLog;
import ru.otus.financetracker.domain.audit.TransactionAuditState;

@Repository
public class JpaAuditLogRepository implements AuditLogRepository {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public JpaAuditLogRepository(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public void save(AuditLog auditLog) {
        auditLogJpaRepository.saveAndFlush(new AuditLogJpaEntity(
                auditLog.id(), auditLog.actorUserId(), auditLog.entityType(), auditLog.entityId(), auditLog.action(),
                auditLog.occurredAt(), toJson(auditLog.beforeState()), toJson(auditLog.afterState())
        ));
    }

    private JsonNode toJson(TransactionAuditState state) {
        if (state == null) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("categoryId", state.categoryId().toString());
        node.put("amount", state.amount().toPlainString());
        node.put("currency", state.currency());
        node.put("exchangeRateToBase", state.exchangeRateToBase().toPlainString());
        node.put("transactionDate", state.transactionDate().toString());
        if (state.description() == null) {
            node.putNull("description");
        } else {
            node.put("description", state.description());
        }
        node.put("transactionType", state.transactionType().name());
        return node;
    }
}
