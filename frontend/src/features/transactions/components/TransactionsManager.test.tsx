import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import type { Category, PageResponse, Transaction } from "../../../shared/api/models";
import { NotificationProvider } from "../../../shared/notifications/NotificationProvider";
import { TransactionsManager } from "./TransactionsManager";

const transaction: Transaction = { id: "123e4567-e89b-42d3-a456-426614174000", categoryId: "123e4567-e89b-42d3-a456-426614174001", amount: "12.3400", currency: "USD", exchangeRateToBase: "1.00000000", transactionDate: "2026-09-17", description: "Groceries", transactionType: "EXPENSE", createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 0 };
const category: Category = { id: transaction.categoryId, name: "Groceries", transactionType: "EXPENSE", icon: "🛒", color: "#2457D6", createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 0 };

function page(items: Transaction[], number = 0, totalPages = 1): PageResponse<Transaction> { return { items, page: { number, size: 20, totalElements: items.length, totalPages } }; }
function categoryPage(items: Category[]): PageResponse<Category> { return { items, page: { number: 0, size: 20, totalElements: items.length, totalPages: 1 } }; }
function response(body: unknown, status = 200): Response { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
function mockFetch(transactions: Response[]): ReturnType<typeof vi.spyOn> {
  return vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("/categories") ? response(categoryPage([category])) : transactions.shift() ?? response(page([]))));
}
function renderTransactions(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) { return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>; }
  render(<TransactionsManager />, { wrapper: Providers });
}

afterEach(() => { vi.restoreAllMocks(); Object.defineProperty(window, "matchMedia", { configurable: true, value: undefined }); });

test("loads the next transaction page", async () => {
  mockFetch([response(page([transaction], 0, 2)), response(page([{ ...transaction, id: "123e4567-e89b-42d3-a456-426614174002", description: "Salary" }], 1, 2))]);
  renderTransactions();
  await screen.findAllByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Next page" }));
  expect(await screen.findByText("Salary")).toBeTruthy();
});

test("submits a new transaction form", async () => {
  const fetchMock = mockFetch([response(page([])), response(transaction, 201), response(page([transaction]))]);
  renderTransactions();
  await screen.findByText("No transactions match these filters.");
  fireEvent.click(screen.getByRole("button", { name: "Add transaction" }));
  fireEvent.change(screen.getAllByLabelText("Category")[0], { target: { value: transaction.categoryId } });
  fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "12.34" } });
  fireEvent.change(screen.getByLabelText("Date"), { target: { value: "2026-09-17" } });
  fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Groceries" } });
  fireEvent.click(screen.getByRole("button", { name: "Save transaction" }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));
  let postCall: [RequestInfo | URL, RequestInit | undefined] | undefined;
  for (const call of fetchMock.mock.calls) {
    if (String(call[0]).endsWith("/transactions") && call[1]?.method === "POST") postCall = call as [RequestInfo | URL, RequestInit | undefined];
  }
  expect(postCall).toBeDefined();
  expect(JSON.parse(String(postCall?.[1]?.body))).toEqual({ categoryId: transaction.categoryId, amount: "12.34", currency: "USD", exchangeRateToBase: "1", transactionDate: "2026-09-17", description: "Groceries", transactionType: "EXPENSE" });
});

test("uses card layout for small screens", async () => {
  Object.defineProperty(window, "matchMedia", { configurable: true, value: () => ({ matches: true, addEventListener: () => undefined, removeEventListener: () => undefined }) });
  mockFetch([response(page([transaction]))]);
  renderTransactions();
  expect(await screen.findByRole("list", { name: "Transaction cards" })).toBeTruthy();
  expect(screen.queryByRole("table")).toBeNull();
});
