export type TransactionType = "INCOME" | "EXPENSE";

export type ApiErrorCode =
  | "VALIDATION_FAILED"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "PAYLOAD_TOO_LARGE"
  | "RATE_LIMITED"
  | "INTERNAL_ERROR";

export interface ApiViolation {
  field: string;
  code: string;
  message: string;
}

export interface ApiError {
  code: ApiErrorCode;
  message: string;
  traceId: string | null;
  violations: ApiViolation[];
}

export interface PageResponse<T> {
  items: T[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface CurrentUser { id: string; email: string; displayName: string; baseCurrency: string; }
export interface LoginResponse { accessToken: string; tokenType: string; expiresAt: string; }
export interface Category { id: string; name: string; transactionType: TransactionType; icon: string; color: string; createdAt: string; updatedAt: string; version: number; }
export interface Transaction { id: string; categoryId: string; amount: string; currency: string; exchangeRateToBase: string; transactionDate: string; description: string | null; transactionType: TransactionType; createdAt: string; updatedAt: string; version: number; }
export interface TransactionImportPreview { rows: Array<{ lineNumber: number; categoryId: string; amount: string; currency: string; exchangeRateToBase: string; transactionDate: string; description: string | null; transactionType: string; }>; lineErrors: Array<{ lineNumber: number; field: string; code: string; message: string; }>; }
export interface TransactionImportConfirmation { importedCount: number; }
export interface Budget { id: string; categoryId: string; budgetMonth: string; limitAmount: string; currency: string; spentAmount: string; remainingAmount: string; percentage: string; createdAt: string; updatedAt: string; version: number; }
export interface RecurringTransaction { id: string; categoryId: string; amount: string; currency: string; exchangeRateToBase: string; description: string | null; transactionType: TransactionType; dayOfMonth: number; startDate: string; nextOccurrenceDate: string; active: boolean; createdAt: string; updatedAt: string; version: number; }
export type AuditEntityType = "TRANSACTION" | "BUDGET";
export type AuditAction = "CREATE" | "UPDATE" | "DELETE";
export interface AuditLog { id: string; entityType: AuditEntityType; entityId: string; action: AuditAction; occurredAt: string; beforeState: Record<string, string | null> | null; afterState: Record<string, string | null> | null; }
export interface DashboardCategoryExpense { categoryId: string; categoryName: string; categoryIcon: string; categoryColor: string; amount: string; }
export interface Dashboard { month: string; expensesByCategory: DashboardCategoryExpense[]; topExpenseCategories: DashboardCategoryExpense[]; }
export interface SpendingTrend { endMonth: string; months: Array<{ month: string; amount: string }>; }
