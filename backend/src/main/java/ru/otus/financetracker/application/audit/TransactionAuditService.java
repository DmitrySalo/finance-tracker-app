package ru.otus.financetracker.application.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import ru.otus.financetracker.domain.audit.AuditAction;
import ru.otus.financetracker.domain.audit.AuditLog;
import ru.otus.financetracker.domain.audit.TransactionAuditState;
import ru.otus.financetracker.domain.transactions.Transaction;

@Service
public class TransactionAuditService {

    private static final String TRANSACTION_ENTITY_TYPE = "TRANSACTION";

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public TransactionAuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public void recordCreate(UUID actorUserId, Transaction transaction) {
        save(actorUserId, transaction.id(), AuditAction.CREATE, null, stateOf(transaction));
    }

    public void recordUpdate(UUID actorUserId, Transaction before, Transaction after) {
        save(actorUserId, after.id(), AuditAction.UPDATE, stateOf(before), stateOf(after));
    }

    public void recordDelete(UUID actorUserId, Transaction transaction) {
        save(actorUserId, transaction.id(), AuditAction.DELETE, stateOf(transaction), null);
    }

    private void save(
            UUID actorUserId,
            UUID entityId,
            AuditAction action,
            TransactionAuditState beforeState,
            TransactionAuditState afterState
    ) {
        auditLogRepository.save(new AuditLog(
                UUID.randomUUID(),
                actorUserId,
                TRANSACTION_ENTITY_TYPE,
                entityId,
                action,
                clock.instant(),
                beforeState,
                afterState
        ));
    }

    private TransactionAuditState stateOf(Transaction transaction) {
        return new TransactionAuditState(
                transaction.categoryId(),
                transaction.amount(),
                transaction.currency(),
                transaction.exchangeRateToBase(),
                transaction.transactionDate(),
                transaction.description(),
                transaction.transactionType()
        );
    }
}
