import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { DashboardManager } from "./DashboardManager";

const dashboard = { month: "2026-09", expensesByCategory: [{ categoryId: "123e4567-e89b-42d3-a456-426614174001", categoryName: "Groceries", categoryIcon: "🛒", categoryColor: "#2457D6", amount: "40.00" }], topExpenseCategories: [{ categoryId: "123e4567-e89b-42d3-a456-426614174001", categoryName: "Groceries", categoryIcon: "🛒", categoryColor: "#2457D6", amount: "40.00" }] };
const trend = { endMonth: "2026-09", months: [{ month: "2026-04-01", amount: "10.00" }, { month: "2026-05-01", amount: "20.00" }, { month: "2026-06-01", amount: "30.00" }, { month: "2026-07-01", amount: "40.00" }, { month: "2026-08-01", amount: "50.00" }, { month: "2026-09-01", amount: "60.00" }] };

function response(body: unknown): Response { return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }); }

function renderDashboard(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) { return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>; }
  render(<DashboardManager />, { wrapper: Providers });
}

afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });

test("requests the selected month for dashboard and spending trend", async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-08-15T12:00:00Z"));
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("spending-trend") ? response(trend) : response(dashboard)));
  renderDashboard();
  vi.useRealTimers();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  fireEvent.change(screen.getByLabelText("Month"), { target: { value: "2026-09" } });
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
  expect(fetchMock.mock.calls.some(([input]) => String(input).includes("/dashboard?month=2026-09"))).toBe(true);
  expect(fetchMock.mock.calls.some(([input]) => String(input).includes("/dashboard/spending-trend?endMonth=2026-09"))).toBe(true);
});

test("does not request an invalid month when the month picker is cleared", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("spending-trend") ? response(trend) : response(dashboard)));
  renderDashboard();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  fireEvent.change(screen.getByLabelText("Month"), { target: { value: "" } });
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test("keeps the spending trend visible when the selected month has no categories", async () => {
  const dashboardWithoutCategories = { ...dashboard, expensesByCategory: [], topExpenseCategories: [] };
  vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("spending-trend") ? response(trend) : response(dashboardWithoutCategories)));
  renderDashboard();

  expect(await screen.findByRole("img", { name: /Six-month spending trend/ })).toBeTruthy();
  expect(screen.getByText("No expense categories for this month.")).toBeTruthy();
  expect(screen.queryByText("No expense data is available for this period.")).toBeNull();
});

test("shows an empty state when the selected month and trend contain no expenses", async () => {
  const emptyDashboard = { ...dashboard, expensesByCategory: [], topExpenseCategories: [] };
  const emptyTrend = { ...trend, months: trend.months.map((entry) => ({ ...entry, amount: "0.00" })) };
  vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("spending-trend") ? response(emptyTrend) : response(emptyDashboard)));
  renderDashboard();

  expect(await screen.findByText("No expense data is available for this period.")).toBeTruthy();
  expect(screen.queryByRole("img", { name: /Six-month spending trend/ })).toBeNull();
});

test("provides accessible textual alternatives for both charts", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("spending-trend") ? response(trend) : response(dashboard)));
  renderDashboard();
  expect((await screen.findByRole("img", { name: /Expenses by category/ })).getAttribute("aria-label")).toContain("Groceries: 40.00");
  expect(screen.getByRole("img", { name: /Six-month spending trend/ }).getAttribute("aria-label")).toContain("2026-09-01: 60.00");
});

test("shows loading and error states", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 500 }));
  renderDashboard();
  expect((await screen.findByRole("status")).textContent).toBe("Loading dashboard…");
  expect((await screen.findByRole("alert")).textContent).toBe("We could not load the dashboard. Please refresh the page.");
});
