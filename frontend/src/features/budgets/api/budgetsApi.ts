import { apiRequest, apiRequestVoid } from "../../../shared/api/client";
import type { Budget, PageResponse } from "../../../shared/api/models";

export const budgetQueryKey = ["budgets"] as const;

export interface BudgetInput {
  categoryId: string;
  budgetMonth: string;
  limitAmount: string;
  currency: string;
}

function listBudgetPage(page: number, signal?: AbortSignal): Promise<PageResponse<Budget>> {
  return apiRequest<PageResponse<Budget>>(`/budgets?page=${page}&size=100`, { signal });
}

export async function listAllBudgets(signal?: AbortSignal): Promise<Budget[]> {
  const budgets: Budget[] = [];
  let page = 0;

  do {
    const response = await listBudgetPage(page, signal);
    budgets.push(...response.items);
    page += 1;
    if (page >= response.page.totalPages) {
      return budgets;
    }
  } while (!signal?.aborted);

  return budgets;
}

export function createBudget(input: BudgetInput): Promise<Budget> {
  return apiRequest<Budget>("/budgets", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateBudget(budget: Budget, input: BudgetInput): Promise<Budget> {
  return apiRequest<Budget>(`/budgets/${budget.id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...input, version: budget.version }),
  });
}

export function deleteBudget(budget: Budget): Promise<void> {
  return apiRequestVoid(`/budgets/${budget.id}?version=${encodeURIComponent(String(budget.version))}`, {
    method: "DELETE",
  });
}
