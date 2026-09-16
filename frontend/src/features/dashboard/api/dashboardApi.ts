import { apiRequest } from "../../../shared/api/client";
import type { Dashboard, SpendingTrend } from "../../../shared/api/models";

export function getDashboard(month: string, signal?: AbortSignal): Promise<Dashboard> {
  return apiRequest<Dashboard>(`/dashboard?month=${encodeURIComponent(month)}`, { signal });
}

export function getSpendingTrend(endMonth: string, signal?: AbortSignal): Promise<SpendingTrend> {
  return apiRequest<SpendingTrend>(`/dashboard/spending-trend?endMonth=${encodeURIComponent(endMonth)}`, { signal });
}
