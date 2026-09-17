package ru.otus.financetracker.domain.audit;

public sealed interface AuditState permits TransactionAuditState, BudgetAuditState {
}
