import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import type { Budget, Category, PageResponse } from "../../../shared/api/models";
import { NotificationProvider } from "../../../shared/notifications/NotificationProvider";
import { BudgetsManager } from "./BudgetsManager";

const category: Category = { id: "123e4567-e89b-42d3-a456-426614174001", name: "Groceries", transactionType: "EXPENSE", icon: "🛒", color: "#2457D6", createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 0 };

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
}

function budgetPage(budget: Budget): PageResponse<Budget> {
  return { items: [budget], page: { number: 0, size: 100, totalElements: 1, totalPages: 1 } };
}

function categoryPage(): PageResponse<Category> {
  return { items: [category], page: { number: 0, size: 100, totalElements: 1, totalPages: 1 } };
}

const currentUser = { id: "123e4567-e89b-42d3-a456-426614174002", email: "budget@example.test", displayName: "Budget User", baseCurrency: "EUR" };

function renderBudgets(budget: Budget): void {
  vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => Promise.resolve(String(input).includes("/categories") ? response(categoryPage()) : response(budgetPage(budget))));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.change(screen.getByLabelText("Month"), { target: { value: "2026-09" } });
}

function budget(percentage: string, spentAmount: string, remainingAmount: string): Budget {
  return { id: "123e4567-e89b-42d3-a456-426614174000", categoryId: category.id, budgetMonth: "2026-09-01", limitAmount: "100.0000", currency: "USD", spentAmount, remainingAmount, percentage, createdAt: "2026-09-17T10:00:00Z", updatedAt: "2026-09-17T10:00:00Z", version: 0 };
}

afterEach(() => vi.restoreAllMocks());

test("provides accessible zero-percent budget progress text", async () => {
  renderBudgets(budget("0.00", "0.0000", "100.0000"));

  const progress = await screen.findByRole("progressbar", { name: "Budget progress" });
  expect(progress.getAttribute("aria-valuenow")).toBe("0");
  expect(progress.getAttribute("aria-valuetext")).toBe("0.00% of budget used. Spent 0.0000 USD of 100.0000 USD; 100.0000 USD remaining.");
});

test("provides accessible one-hundred-percent budget progress text", async () => {
  renderBudgets(budget("100.00", "100.0000", "0.0000"));

  const progress = await screen.findByRole("progressbar", { name: "Budget progress" });
  expect(progress.getAttribute("aria-valuenow")).toBe("100");
  expect(progress.getAttribute("aria-valuetext")).toBe("100.00% of budget used. Spent 100.0000 USD of 100.0000 USD; 0.0000 USD remaining.");
});

test("caps visual progress while preserving accessible over-limit text", async () => {
  renderBudgets(budget("125.00", "125.0000", "-25.0000"));

  const progress = await screen.findByRole("progressbar", { name: "Budget progress" });
  expect(progress.getAttribute("aria-valuenow")).toBe("100");
  expect(progress.getAttribute("aria-valuetext")).toBe("125.00% of budget used. Spent 125.0000 USD of 100.0000 USD; -25.0000 USD remaining.");
  expect(await screen.findByText("Over budget (125.00%)")).toBeTruthy();
});

test("creates a budget with the authenticated user's non-USD base currency", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(response(budgetPage(budget("0.00", "0.0000", "100.0000"))))
    .mockResolvedValueOnce(response(categoryPage()))
    .mockResolvedValueOnce(response(currentUser))
    .mockResolvedValueOnce(response(budget("0.00", "0.0000", "100.0000")));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.click(screen.getByRole("button", { name: "Add budget" }));

  const currency = await screen.findByLabelText("Currency");
  await waitFor(() => expect(currency).toHaveProperty("value", "EUR"));
  expect(currency).toHaveProperty("readOnly", true);
  fireEvent.change(screen.getByLabelText("Expense category"), { target: { value: category.id } });
  fireEvent.change(screen.getByLabelText("Limit amount"), { target: { value: "20" } });
  fireEvent.click(screen.getByRole("button", { name: "Save budget" }));

  await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(true));
  const createRequest = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
  expect(JSON.parse(String(createRequest?.[1]?.body))).toMatchObject({
    categoryId: category.id,
    budgetMonth: expect.stringMatching(/^\d{4}-\d{2}-01$/),
    currency: "EUR",
    limitAmount: "20",
  });
});

test("does not allow creation until the authenticated base currency is known", async () => {
  let completeCurrentUser: ((response: Response) => void) | undefined;
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(response(budgetPage(budget("0.00", "0.0000", "100.0000"))))
    .mockResolvedValueOnce(response(categoryPage()))
    .mockImplementationOnce(() => new Promise<Response>((resolve) => { completeCurrentUser = resolve; }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.click(screen.getByRole("button", { name: "Add budget" }));

  expect((await screen.findByRole("status")).textContent).toBe("Loading your base currency…");
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Save budget" }).disabled).toBe(true);
  expect(fetchMock).toHaveBeenCalledTimes(3);
  await act(async () => { completeCurrentUser?.(response(currentUser)); });
});

test("does not allow creation when the authenticated base currency cannot be loaded", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(response(budgetPage(budget("0.00", "0.0000", "100.0000"))))
    .mockResolvedValueOnce(response(categoryPage()))
    .mockResolvedValueOnce(new Response(null, { status: 500 }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.click(screen.getByRole("button", { name: "Add budget" }));

  expect((await screen.findByRole("alert")).textContent).toBe("We could not load your base currency. Please try again.");
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Save budget" }).disabled).toBe(true);
  expect(fetchMock).toHaveBeenCalledTimes(3);
});

test("restores focus to the invoking delete action when deletion is cancelled", async () => {
  renderBudgets(budget("0.00", "0.0000", "100.0000"));

  const deleteAction = await screen.findByRole<HTMLButtonElement>("button", { name: "Delete budget" });
  fireEvent.click(deleteAction);
  fireEvent.click(within(screen.getByRole("dialog", { name: "Delete budget?" })).getByRole("button", { name: "Cancel" }));

  await waitFor(() => expect(document.activeElement).toBe(deleteAction));
});

test("restores focus to Add budget after a successful deletion", async () => {
  const budgetItem = budget("0.00", "0.0000", "100.0000");
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === "DELETE") {
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    return Promise.resolve(String(input).includes("/categories") ? response(categoryPage()) : response(budgetPage(budgetItem)));
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.change(screen.getByLabelText("Month"), { target: { value: "2026-09" } });
  fireEvent.click(await screen.findByRole("button", { name: "Delete budget" }));
  fireEvent.click(within(screen.getByRole("dialog", { name: "Delete budget?" })).getByRole("button", { name: "Delete budget" }));

  await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(true));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "Delete budget?" })).toBeNull());
  await waitFor(() => expect(document.activeElement).toBe(screen.getByRole("button", { name: "Add budget" })));
});

test("keeps focus in the delete dialog while deletion is pending", async () => {
  let completeDelete: ((response: Response) => void) | undefined;
  const budgetItem = budget("0.00", "0.0000", "100.0000");
  vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(response(budgetPage(budgetItem)))
    .mockResolvedValueOnce(response(categoryPage()))
    .mockImplementationOnce(() => new Promise<Response>((resolve) => { completeDelete = resolve; }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<BudgetsManager />, { wrapper: Providers });
  fireEvent.click(await screen.findByRole("button", { name: "Delete budget" }));
  const dialog = screen.getByRole("dialog", { name: "Delete budget?" });
  const deleteConfirmation = within(dialog).getByRole<HTMLButtonElement>("button", { name: "Delete budget" });
  fireEvent.click(deleteConfirmation);

  await waitFor(() => expect(deleteConfirmation.disabled).toBe(true));
  expect(document.activeElement).toBe(within(dialog).getByRole("button", { name: "Cancel" }));
  fireEvent.keyDown(document, { key: "Tab" });
  expect(document.activeElement).toBe(dialog);
  await act(async () => { completeDelete?.(new Response(null, { status: 204 })); });
});
