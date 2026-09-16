import { afterEach, expect, test, vi } from "vitest";
import { setAccessToken } from "./accessToken";
import { apiRequest, apiRequestVoid } from "./client";

afterEach(() => {
  setAccessToken(null);
  vi.restoreAllMocks();
});

test("adds the in-memory bearer token to API requests", async () => {
  setAccessToken("access-token");
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify({ id: "user-id" }), { status: 200 }),
  );

  await apiRequest<{ id: string }>("/auth/me");

  expect(fetchMock).toHaveBeenCalledWith(
    "/api/v1/auth/me",
    expect.objectContaining({ headers: expect.any(Headers) }),
  );
  expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe("Bearer access-token");
});

test("maps the backend error response to a single typed error contract", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(
      JSON.stringify({
        code: "VALIDATION_FAILED",
        message: "Request validation failed.",
        traceId: "trace-id",
        violations: [{ field: "amount", code: "POSITIVE", message: "Must be positive." }],
      }),
      { status: 400 },
    ),
  );

  await expect(apiRequest("/transactions")).rejects.toMatchObject({
    status: 400,
    apiError: expect.objectContaining({ code: "VALIDATION_FAILED", traceId: "trace-id" }),
  });
});

test("accepts an empty successful API response", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

  await expect(apiRequestVoid("/categories/category-id", { method: "DELETE" })).resolves.toBeUndefined();
});
