import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { AuditLogManager } from "./AuditLogManager";

function response(body: unknown): Response { return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }); }
function renderManager(): void { const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); function Providers({ children }: { children: ReactNode }) { return <QueryClientProvider client={client}>{children}</QueryClientProvider>; } render(<AuditLogManager />, { wrapper: Providers }); }
afterEach(() => vi.restoreAllMocks());

test("shows before and after states and applies the audit filters", async () => {
  const auditPage = { items: [{ id: "123e4567-e89b-42d3-a456-426614174003", entityType: "BUDGET", entityId: "123e4567-e89b-42d3-a456-426614174004", action: "UPDATE", occurredAt: "2026-09-17T10:00:00Z", beforeState: { limitAmount: "100.0000", currency: "USD" }, afterState: { limitAmount: "125.0000", currency: "USD" } }], page: { number: 0, size: 20, totalElements: 1, totalPages: 1 } };
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(response(auditPage)));
  renderManager(); await screen.findByText("Before"); fireEvent.change(screen.getByLabelText("Resource type"), { target: { value: "BUDGET" } }); fireEvent.change(screen.getByLabelText("Resource ID"), { target: { value: "123e4567-e89b-42d3-a456-426614174004" } }); fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([input]) => String(input).includes("entityType=BUDGET") && String(input).includes("entityId=123e4567-e89b-42d3-a456-426614174004"))).toBe(true)); expect(await screen.findByText("100.0000")).toBeTruthy(); expect(screen.getByText("125.0000")).toBeTruthy();
});
