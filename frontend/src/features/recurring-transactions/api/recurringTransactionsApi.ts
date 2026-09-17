import { apiRequest, apiRequestVoid } from "../../../shared/api/client";
import type { PageResponse, RecurringTransaction, TransactionType } from "../../../shared/api/models";

export const recurringTransactionQueryKey = ["recurring-transactions"] as const;

export interface RecurringTransactionInput {
  categoryId: string;
  amount: string;
  currency: string;
  exchangeRateToBase: string;
  description: string | null;
  transactionType: TransactionType;
  dayOfMonth: number;
  startDate: string;
  active: boolean;
}

export function listRecurringTransactions(page: number, signal?: AbortSignal): Promise<PageResponse<RecurringTransaction>> {
  return apiRequest<PageResponse<RecurringTransaction>>(`/recurring-transactions?page=${page}&size=20`, { signal });
}

export function createRecurringTransaction(input: RecurringTransactionInput): Promise<RecurringTransaction> {
  return apiRequest<RecurringTransaction>("/recurring-transactions", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
}

export function updateRecurringTransaction(rule: RecurringTransaction, input: Partial<RecurringTransactionInput>): Promise<RecurringTransaction> {
  return apiRequest<RecurringTransaction>(`/recurring-transactions/${rule.id}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...input, version: rule.version }) });
}

export function deleteRecurringTransaction(rule: RecurringTransaction): Promise<void> {
  return apiRequestVoid(`/recurring-transactions/${rule.id}?version=${encodeURIComponent(String(rule.version))}`, { method: "DELETE" });
}
