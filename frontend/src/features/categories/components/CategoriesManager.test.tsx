import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { NotificationProvider } from "../../../shared/notifications/NotificationProvider";
import type { Category, PageResponse } from "../../../shared/api/models";
import { CategoriesManager } from "./CategoriesManager";

const groceries: Category = {
  id: "01234567-89ab-cdef-0123-456789abcdef",
  name: "Groceries",
  transactionType: "EXPENSE",
  icon: "🛒",
  color: "#2457D6",
  createdAt: "2026-09-17T10:00:00Z",
  updatedAt: "2026-09-17T10:00:00Z",
  version: 0,
};

function categoryPage(items: Category[], number = 0, totalElements = items.length, totalPages = 1): PageResponse<Category> {
  return { items, page: { number, size: 20, totalElements, totalPages } };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderCategories(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<CategoriesManager />, { wrapper: Providers });
}

afterEach(() => {
  vi.restoreAllMocks();
});

test("invalidates the category query after creating a category", async () => {
  const newCategory = { ...groceries, id: "fedcba98-7654-3210-fedc-ba9876543210", name: "Salary", transactionType: "INCOME" as const };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries])))
    .mockResolvedValueOnce(jsonResponse(newCategory, 201))
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries, newCategory])));

  renderCategories();

  expect(await screen.findByText("Groceries")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Add category" }));
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Salary" } });
  fireEvent.change(screen.getByLabelText("Type"), { target: { value: "INCOME" } });
  fireEvent.click(screen.getByRole("button", { name: "Save category" }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({
    name: "Salary", transactionType: "INCOME", icon: "🏠", color: "#2457D6",
  });
  expect(await screen.findByText("Salary")).toBeTruthy();
});

test("invalidates the category query after updating a category", async () => {
  const updatedCategory = { ...groceries, name: "Food", version: 1 };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries])))
    .mockResolvedValueOnce(jsonResponse(updatedCategory))
    .mockResolvedValueOnce(jsonResponse(categoryPage([updatedCategory])));

  renderCategories();

  await screen.findByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Edit Groceries" }));
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Food" } });
  fireEvent.click(screen.getByRole("button", { name: "Save category" }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toMatchObject({ name: "Food", version: 0 });
  expect(await screen.findByText("Food")).toBeTruthy();
});

test("invalidates the category query after deleting a category", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries])))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(jsonResponse(categoryPage([])));

  renderCategories();

  await screen.findByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Delete Groceries" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete category" }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(await screen.findByText("No categories yet. Add one to start organizing your finances.")).toBeTruthy();
});

test("returns to the preceding page after deleting its last category", async () => {
  const previousPageCategory = { ...groceries, id: "fedcba98-7654-3210-fedc-ba9876543210", name: "Salary", transactionType: "INCOME" as const };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([previousPageCategory], 0, 21, 2)))
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries], 1, 21, 2)))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(jsonResponse(categoryPage([], 1, 20, 1)))
    .mockResolvedValueOnce(jsonResponse(categoryPage([previousPageCategory], 0, 20, 1)));

  renderCategories();

  await screen.findByText("Salary");
  fireEvent.click(screen.getByRole("button", { name: "Next page" }));
  expect(await screen.findByText("Groceries")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Delete Groceries" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete category" }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));
  expect(await screen.findByText("Salary")).toBeTruthy();
});

test("replaces the edit form values when selecting another category", async () => {
  const salary = { ...groceries, id: "fedcba98-7654-3210-fedc-ba9876543210", name: "Salary", transactionType: "INCOME" as const };
  vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(jsonResponse(categoryPage([groceries, salary])));

  renderCategories();

  await screen.findByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Edit Groceries" }));
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Changed groceries" } });
  fireEvent.click(screen.getByRole("button", { name: "Edit Salary" }));

  expect(screen.getByLabelText("Name")).toHaveProperty("value", "Salary");
  expect(screen.getByLabelText("Type")).toHaveProperty("value", "INCOME");
});

test("displays a server validation error next to the invalid field", async () => {
  vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([])))
    .mockResolvedValueOnce(jsonResponse({
      code: "VALIDATION_FAILED",
      message: "Request validation failed.",
      traceId: null,
      violations: [{ field: "name", code: "DUPLICATE", message: "A category with this name already exists." }],
    }, 400));

  renderCategories();

  await screen.findByText("No categories yet. Add one to start organizing your finances.");
  fireEvent.click(screen.getByRole("button", { name: "Add category" }));
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Groceries" } });
  fireEvent.click(screen.getByRole("button", { name: "Save category" }));

  expect(await screen.findByText("The category contains an invalid value.")).toBeTruthy();
  expect(screen.getByRole("textbox", { name: "Name" }).getAttribute("aria-invalid")).toBe("true");
});

test("displays a conflict error when an in-use category cannot be deleted", async () => {
  vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries])))
    .mockResolvedValueOnce(jsonResponse({
      code: "CONFLICT",
      message: "Category is in use.",
      traceId: null,
      violations: [],
    }, 409));

  renderCategories();

  await screen.findByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Delete Groceries" }));
  expect(screen.getByRole("dialog", { name: "Delete category?" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Delete category" }));

  expect(await screen.findByText("This category cannot be deleted because it is in use.")).toBeTruthy();
});

test("keeps focus in the delete confirmation while deletion is pending", async () => {
  let completeDelete: (response: Response) => void;
  vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(jsonResponse(categoryPage([groceries])))
    .mockImplementationOnce(() => new Promise<Response>((resolve) => { completeDelete = resolve; }))
    .mockResolvedValueOnce(jsonResponse(categoryPage([])));

  renderCategories();

  await screen.findByText("Groceries");
  fireEvent.click(screen.getByRole("button", { name: "Delete Groceries" }));
  const deleteConfirmation = screen.getByRole<HTMLButtonElement>("button", { name: "Delete category" });
  fireEvent.click(deleteConfirmation);

  await waitFor(() => expect(deleteConfirmation.disabled).toBe(true));
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cancel" }));
  fireEvent.keyDown(document, { key: "Tab" });
  expect(document.activeElement).toBe(screen.getByRole("dialog", { name: "Delete category?" }));
  await act(async () => {
    completeDelete(new Response(null, { status: 204 }));
  });
});
