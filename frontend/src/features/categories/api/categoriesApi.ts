import { apiRequest, apiRequestVoid } from "../../../shared/api/client";
import type { Category, PageResponse, TransactionType } from "../../../shared/api/models";

export const categoryQueryKey = ["categories"] as const;

export interface CategoryInput {
  name: string;
  transactionType: TransactionType;
  icon: string;
  color: string;
}

export function listCategories(page: number, signal?: AbortSignal): Promise<PageResponse<Category>> {
  return apiRequest<PageResponse<Category>>(`/categories?page=${page}&size=20`, { signal });
}

export function createCategory(input: CategoryInput): Promise<Category> {
  return apiRequest<Category>("/categories", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateCategory(category: Category, input: CategoryInput): Promise<Category> {
  return apiRequest<Category>(`/categories/${category.id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...input, version: category.version }),
  });
}

export function deleteCategory(categoryId: string): Promise<void> {
  return apiRequestVoid(`/categories/${categoryId}`, { method: "DELETE" });
}
