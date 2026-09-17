import { afterEach, expect, test, vi } from "vitest";
import { listTransactions } from "./transactionsApi";

afterEach(() => {
  vi.restoreAllMocks();
});

test("serializes non-empty transaction filters with paging and sort", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ items: [], page: { number: 2, size: 20, totalElements: 0, totalPages: 0 } }), { status: 200 }));

  await listTransactions({ fromDate: "2026-09-01", toDate: "2026-09-30", categoryId: "01234567-89ab-cdef-0123-456789abcdef", minAmount: "10", maxAmount: "100", transactionType: "EXPENSE" }, 2, "amount,asc");

  expect(fetchMock).toHaveBeenCalledWith("/api/v1/transactions?page=2&size=20&sort=amount%2Casc&fromDate=2026-09-01&toDate=2026-09-30&categoryId=01234567-89ab-cdef-0123-456789abcdef&minAmount=10&maxAmount=100&transactionType=EXPENSE", expect.any(Object));
});
