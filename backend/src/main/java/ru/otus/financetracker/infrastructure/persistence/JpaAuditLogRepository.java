package ru.otus.financetracker.infrastructure.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.audit.AuditLogRepository;
import ru.otus.financetracker.domain.audit.AuditLog;
import ru.otus.financetracker.domain.audit.AuditLogEntry;
import ru.otus.financetracker.domain.audit.AuditState;
import ru.otus.financetracker.domain.audit.BudgetAuditState;
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
                auditLog.id(),
                auditLog.actorUserId(),
                auditLog.entityType(),
                auditLog.entityId(),
                auditLog.action(),
                auditLog.occurredAt(),
                toJson(auditLog.beforeState()),
                toJson(auditLog.afterState())
        ));
    }

    @Override
    public Page<AuditLogEntry> findAllByActorUserIdAndEntityType(
            UUID actorUserId,
            String entityType,
            UUID entityId,
            Pageable pageable
    ) {
        Page<AuditLogJpaEntity> entries = entityId == null
                ? auditLogJpaRepository.findAllByActorUserIdAndEntityType(actorUserId, entityType, pageable)
                : auditLogJpaRepository.findAllByActorUserIdAndEntityTypeAndEntityId(
                        actorUserId,
                        entityType,
                        entityId,
                        pageable
                );
        return entries.map(entry -> new AuditLogEntry(
                entry.id(),
                entry.entityType(),
                entry.entityId(),
                entry.action(),
                entry.occurredAt(),
                toMap(entry.beforeState()),
                toMap(entry.afterState())
        ));
    }

    private Map<String, Object> toMap(JsonNode state) {
        if (state == null || state.isNull()) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        state.properties().forEach(entry ->
                values.put(entry.getKey(), jsonValue(entry.getValue()))
        );
        return Collections.unmodifiableMap(values);
    }

    private Object jsonValue(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        return value.toString();
    }

    private JsonNode toJson(AuditState state) {
        if (state == null) {
            return null;
        }
        if (state instanceof TransactionAuditState transactionState) {
            return transactionStateToJson(transactionState);
        }
        return budgetStateToJson((BudgetAuditState) state);
    }

    private JsonNode transactionStateToJson(TransactionAuditState state) {
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

    private JsonNode budgetStateToJson(BudgetAuditState state) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("categoryId", state.categoryId().toString());
        node.put("budgetMonth", state.budgetMonth().toString());
        node.put("limitAmount", state.limitAmount().toPlainString());
        node.put("currency", state.currency());
        return node;
    }
}
