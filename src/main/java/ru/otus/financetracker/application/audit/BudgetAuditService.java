package ru.otus.financetracker.application.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import ru.otus.financetracker.domain.audit.AuditAction;
import ru.otus.financetracker.domain.audit.AuditLog;
import ru.otus.financetracker.domain.audit.BudgetAuditState;
import ru.otus.financetracker.domain.budgets.Budget;

@Service
public class BudgetAuditService {

    private static final String BUDGET_ENTITY_TYPE = "BUDGET";

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public BudgetAuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public void recordCreate(UUID actorUserId, Budget budget) {
        save(actorUserId, budget.id(), AuditAction.CREATE, null, stateOf(budget));
    }

    public void recordUpdate(UUID actorUserId, Budget before, Budget after) {
        save(actorUserId, after.id(), AuditAction.UPDATE, stateOf(before), stateOf(after));
    }

    public void recordDelete(UUID actorUserId, Budget budget) {
        save(actorUserId, budget.id(), AuditAction.DELETE, stateOf(budget), null);
    }

    private void save(UUID actorUserId, UUID entityId, AuditAction action, BudgetAuditState beforeState,
                      BudgetAuditState afterState) {
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), actorUserId, BUDGET_ENTITY_TYPE, entityId, action,
                clock.instant(), beforeState, afterState));
    }

    private BudgetAuditState stateOf(Budget budget) {
        return new BudgetAuditState(budget.categoryId(), budget.budgetMonth(), budget.limitAmount(), budget.currency());
    }
}
