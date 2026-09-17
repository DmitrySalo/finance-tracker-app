import { apiRequest } from "../../../shared/api/client";
import type { AuditEntityType, AuditLog, PageResponse } from "../../../shared/api/models";

export const auditLogQueryKey = ["audit-logs"] as const;

export function listAuditLogs(entityType: AuditEntityType, entityId: string, page: number, signal?: AbortSignal): Promise<PageResponse<AuditLog>> {
  const parameters = new URLSearchParams({ entityType, page: String(page), size: "20" });
  if (entityId !== "") parameters.set("entityId", entityId);
  return apiRequest<PageResponse<AuditLog>>(`/audit-logs?${parameters.toString()}`, { signal });
}
