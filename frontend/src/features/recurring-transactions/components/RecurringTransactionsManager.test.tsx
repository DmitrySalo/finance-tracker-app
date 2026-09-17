import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { NotificationProvider } from "../../../shared/notifications/NotificationProvider";
import { RecurringTransactionsManager } from "./RecurringTransactionsManager";

const category = { id: "123e4567-e89b-42d3-a456-426614174001", name: "Rent", transactionType: "EXPENSE", icon: "H", color: "#2457D6", createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 0 };
const rule = { id: "123e4567-e89b-42d3-a456-426614174002", categoryId: category.id, amount: "1200.0000", currency: "USD", exchangeRateToBase: "1.00000000", description: "Monthly rent", transactionType: "EXPENSE" as const, dayOfMonth: 1, startDate: "2026-09-01", nextOccurrenceDate: "2026-10-01", active: true, createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 4 };
function response(body: unknown): Response { return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }); }
function renderManager(): void { const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); function Providers({ children }: { children: ReactNode }) { return <QueryClientProvider client={client}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>; } render(<RecurringTransactionsManager />, { wrapper: Providers }); }
afterEach(() => vi.restoreAllMocks());

test("pauses and activates a recurring rule", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((_input, init) => init?.method === "PATCH" ? Promise.resolve(response({ ...rule, active: false, version: 5 })) : Promise.resolve(response({ items: [rule], page: { number: 0, size: 20, totalElements: 1, totalPages: 1 } })));
  renderManager();
  expect(await screen.findByText("1200.00 USD · Expense")).toBeTruthy();
  fireEvent.click(await screen.findByRole("button", { name: "Pause rule" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PATCH" && String(init.body).includes('"active":false'))).toBe(true));
  await waitFor(() => expect(screen.getByText("Recurring rule paused.")).toBeTruthy());
});

test("submits all recurring rule form fields", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((input, init) => String(input).includes("/categories") ? Promise.resolve(response({ items: [category], page: { number: 0, size: 100, totalElements: 1, totalPages: 1 } })) : init?.method === "POST" ? Promise.resolve(response(rule)) : Promise.resolve(response({ items: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } })));
  renderManager(); await screen.findByText("No recurring rules yet. Add one to schedule future transactions."); fireEvent.click(screen.getByRole("button", { name: "Add recurring rule" }));
  await screen.findByRole("option", { name: "H Rent" }); const categoryField = await screen.findByLabelText<HTMLSelectElement>("Category");
  fireEvent.change(categoryField, { target: { value: category.id } }); fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "25" } }); fireEvent.click(screen.getByLabelText("Start date")); fireEvent.click(screen.getByRole("button", { name: "Next month" })); fireEvent.click(screen.getByRole("button", { name: "February 1, 2026" })); fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Subscription" } }); fireEvent.click(screen.getByRole("button", { name: "Save rule" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(true));
  const create = fetchMock.mock.calls.find(([, init]) => init?.method === "POST"); expect(JSON.parse(String(create?.[1]?.body))).toMatchObject({ categoryId: category.id, amount: "25", startDate: "2026-02-01", description: "Subscription", active: true });
});
