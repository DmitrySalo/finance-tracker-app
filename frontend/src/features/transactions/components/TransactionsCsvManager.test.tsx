import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { NotificationProvider } from "../../../shared/notifications/NotificationProvider";
import { TransactionsCsvManager } from "./TransactionsCsvManager";

const transactionApiMocks = vi.hoisted(() => ({ confirmTransactionImport: vi.fn(), exportTransactions: vi.fn(), previewTransactionImport: vi.fn() }));

vi.mock("../api/transactionsApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/transactionsApi")>();
  return {
    ...actual,
    confirmTransactionImport: transactionApiMocks.confirmTransactionImport,
    exportTransactions: transactionApiMocks.exportTransactions,
    previewTransactionImport: transactionApiMocks.previewTransactionImport,
  };
});

function renderCsvManager(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Providers({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}><NotificationProvider>{children}</NotificationProvider></QueryClientProvider>;
  }

  render(<TransactionsCsvManager filters={{ fromDate: "2026-09-01", toDate: "", categoryId: "", minAmount: "", maxAmount: "", transactionType: "" }} />, { wrapper: Providers });
}

function uploadCsv(contents: string): void {
  const file = new File([contents], "transactions.csv", { type: "text/csv" });
  fireEvent.change(screen.getByLabelText("CSV file"), { target: { files: [file] } });
}

afterEach(() => {
  vi.clearAllMocks();
});

test("uploads a CSV and maps matching headers", async () => {
  renderCsvManager();

  uploadCsv("categoryId,amount,currency,exchangeRateToBase,transactionDate,description,transactionType\n");

  expect(await screen.findByText("Column mapping")).toBeTruthy();
  expect(screen.getByLabelText<HTMLSelectElement>("Category ID (required)").value).toBe("categoryId");
  expect(screen.getByLabelText<HTMLSelectElement>("Description (optional)").value).toBe("description");
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Preview import" }).disabled).toBe(false);
});

test("exports transactions using the applied filters", async () => {
  transactionApiMocks.exportTransactions.mockResolvedValue(new Blob(["header"], { type: "text/csv" }));
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:transactions");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
  renderCsvManager();

  fireEvent.click(screen.getByRole("button", { name: "Export CSV" }));

  await waitFor(() => expect(transactionApiMocks.exportTransactions).toHaveBeenCalledWith({ fromDate: "2026-09-01", toDate: "", categoryId: "", minAmount: "", maxAmount: "", transactionType: "" }));
});

test("sends the selected mapping for preview", async () => {
  transactionApiMocks.previewTransactionImport.mockResolvedValue({ rows: [], lineErrors: [] });
  renderCsvManager();

  uploadCsv("category,amount,currency,rate,date,type\n");
  await screen.findByText("Column mapping");
  fireEvent.change(screen.getByLabelText("Category ID (required)"), { target: { value: "category" } });
  fireEvent.change(screen.getByLabelText("Exchange rate to base currency (required)"), { target: { value: "rate" } });
  fireEvent.change(screen.getByLabelText("Transaction date (required)"), { target: { value: "date" } });
  fireEvent.change(screen.getByLabelText("Transaction type (required)"), { target: { value: "type" } });
  fireEvent.click(screen.getByRole("button", { name: "Preview import" }));

  await waitFor(() => expect(transactionApiMocks.previewTransactionImport).toHaveBeenCalledTimes(1));
  expect(transactionApiMocks.previewTransactionImport.mock.calls[0]?.[1]).toEqual({ categoryId: "category", amount: "amount", currency: "currency", exchangeRateToBase: "rate", transactionDate: "date", transactionType: "type" });
});

test("disables confirmation when preview reports line errors", async () => {
  transactionApiMocks.previewTransactionImport.mockResolvedValue({ rows: [], lineErrors: [{ lineNumber: 2, field: "amount", code: "INVALID", message: "Must be positive." }] });
  renderCsvManager();

  uploadCsv("categoryId,amount,currency,exchangeRateToBase,transactionDate,transactionType\n");
  await screen.findByText("Column mapping");
  fireEvent.click(screen.getByRole("button", { name: "Preview import" }));

  expect(await screen.findByText("Line 2, amount: Must be positive.")).toBeTruthy();
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Confirm import" }).disabled).toBe(true);
});

test("confirms an error-free preview with the selected file and mapping", async () => {
  transactionApiMocks.previewTransactionImport.mockResolvedValue({ rows: [], lineErrors: [] });
  transactionApiMocks.confirmTransactionImport.mockResolvedValue({ importedCount: 1 });
  renderCsvManager();

  uploadCsv("categoryId,amount,currency,exchangeRateToBase,transactionDate,transactionType\n");
  await screen.findByText("Column mapping");
  fireEvent.click(screen.getByRole("button", { name: "Preview import" }));
  await screen.findByText("Preview rows");
  fireEvent.click(screen.getByRole("button", { name: "Confirm import" }));

  await waitFor(() => expect(transactionApiMocks.confirmTransactionImport).toHaveBeenCalledTimes(1));
  expect(transactionApiMocks.confirmTransactionImport.mock.calls[0]?.[1]).toEqual({ categoryId: "categoryId", amount: "amount", currency: "currency", exchangeRateToBase: "exchangeRateToBase", transactionDate: "transactionDate", transactionType: "transactionType" });
  expect(await screen.findByText("1 transaction imported.")).toBeTruthy();
});
