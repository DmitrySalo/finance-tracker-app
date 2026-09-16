import { apiRequest, apiRequestBlob, apiRequestVoid } from "../../../shared/api/client";
import type { PageResponse, Transaction, TransactionImportConfirmation, TransactionImportPreview, TransactionType } from "../../../shared/api/models";

export const transactionQueryKey = ["transactions"] as const;

export interface TransactionFilters {
  fromDate: string;
  toDate: string;
  categoryId: string;
  minAmount: string;
  maxAmount: string;
  transactionType: "" | TransactionType;
}

export interface TransactionInput {
  categoryId: string;
  amount: string;
  currency: string;
  exchangeRateToBase: string;
  transactionDate: string;
  description: string | null;
  transactionType: TransactionType;
}

export type TransactionSort = "transactionDate,asc" | "transactionDate,desc" | "amount,asc" | "amount,desc";

export function listTransactions(filters: TransactionFilters, page: number, sort: TransactionSort, signal?: AbortSignal): Promise<PageResponse<Transaction>> {
  const parameters = new URLSearchParams({ page: String(page), size: "20", sort });
  for (const [name, value] of Object.entries(filters)) {
    if (value !== "") {
      parameters.set(name, value);
    }
  }
  return apiRequest<PageResponse<Transaction>>(`/transactions?${parameters.toString()}`, { signal });
}

export function createTransaction(input: TransactionInput): Promise<Transaction> {
  return apiRequest<Transaction>("/transactions", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
}

export function updateTransaction(transaction: Transaction, input: TransactionInput): Promise<Transaction> {
  return apiRequest<Transaction>(`/transactions/${transaction.id}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...input, version: transaction.version }) });
}

export function deleteTransaction(transaction: Transaction): Promise<void> {
  return apiRequestVoid(`/transactions/${transaction.id}?version=${encodeURIComponent(String(transaction.version))}`, { method: "DELETE" });
}

export type TransactionImportColumn = "categoryId" | "amount" | "currency" | "exchangeRateToBase" | "transactionDate" | "description" | "transactionType";
export type TransactionImportMapping = Partial<Record<TransactionImportColumn, string>>;

function filterParameters(filters: TransactionFilters): URLSearchParams {
  const parameters = new URLSearchParams();
  for (const [name, value] of Object.entries(filters)) {
    if (value !== "") parameters.set(name, value);
  }
  return parameters;
}

export function exportTransactions(filters: TransactionFilters): Promise<Blob> {
  const parameters = filterParameters(filters);
  return apiRequestBlob(`/transactions/export${parameters.size > 0 ? `?${parameters.toString()}` : ""}`);
}

function importFormData(file: File, mapping: TransactionImportMapping): FormData {
  const columns = Object.fromEntries(Object.entries(mapping).filter((entry): entry is [string, string] => entry[1] !== ""));
  const formData = new FormData();
  formData.append("file", file);
  formData.append("mapping", new Blob([JSON.stringify({ columns })], { type: "application/json" }));
  return formData;
}

export function previewTransactionImport(file: File, mapping: TransactionImportMapping): Promise<TransactionImportPreview> {
  return apiRequest<TransactionImportPreview>("/transactions/imports/preview", { method: "POST", body: importFormData(file, mapping) });
}

export function confirmTransactionImport(file: File, mapping: TransactionImportMapping): Promise<TransactionImportConfirmation> {
  return apiRequest<TransactionImportConfirmation>("/transactions/imports", { method: "POST", body: importFormData(file, mapping) });
}
