import { afterEach, expect, test, vi } from "vitest";
import { getAccessToken, setAccessToken } from "./accessToken";
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

test("does not clear a newer session after an older request receives 401", async () => {
  setAccessToken("expired-access-token");
  let respondToRequest: ((response: Response) => void) | undefined;
  vi.spyOn(globalThis, "fetch").mockImplementation(() => new Promise<Response>((resolve) => {
    respondToRequest = resolve;
  }));

  const request = apiRequest("/auth/me");
  setAccessToken("new-access-token");
  respondToRequest?.(new Response(null, { status: 401 }));

  await expect(request).rejects.toMatchObject({ status: 401 });
  expect(getAccessToken()).toBe("new-access-token");
});

test("does not clear an active session for a failed public login", async () => {
  setAccessToken("active-access-token");
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 401 }));

  await expect(apiRequest("/auth/login", { clearSessionOnUnauthorized: false })).rejects.toMatchObject({ status: 401 });

  expect(getAccessToken()).toBe("active-access-token");
});
