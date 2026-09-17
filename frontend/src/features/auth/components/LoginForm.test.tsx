import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { App } from "../../../App";
import { setAccessToken } from "../../../shared/api/accessToken";
import { apiRequest } from "../../../shared/api/client";

localStorage.setItem("finance-tracker.locale", "en");

afterEach(async () => {
  await act(async () => setAccessToken(null));
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

test("shows client validation errors before submitting login credentials", async () => {
  window.history.pushState({}, "", "/login");
  const fetchMock = vi.spyOn(globalThis, "fetch");

  render(<App />);
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByText("Enter your email address.")).toBeTruthy();
  expect(screen.getByText("Enter your password.")).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});

test("stores the access token in memory and opens the dashboard after login", async () => {
  window.history.pushState({}, "", "/login");
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
    accessToken: "test-access-token",
    tokenType: "Bearer",
    expiresAt: "2026-09-17T12:00:00Z",
  }), { status: 200 }));

  render(<App />);
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-for-testing" } });
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
  });

  expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/login", expect.objectContaining({ method: "POST" }));
  expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
    email: "person@example.test",
    password: "password-for-testing",
  });
});

test("shows a retry-later message for a rate-limited login", async () => {
  window.history.pushState({}, "", "/login");
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
    code: "RATE_LIMITED", message: "Too many requests.", traceId: null, violations: [],
  }), { status: 429 }));

  render(<App />);
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-for-testing" } });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByText("Too many attempts. Please try again later.")).toBeTruthy();
});

test("clears the session and redirects to sign in after a protected request returns 401", async () => {
  await act(async () => setAccessToken("expired-access-token"));
  window.history.pushState({}, "", "/dashboard");
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
    code: "UNAUTHORIZED",
    message: "Authentication is required.",
    traceId: null,
    violations: [],
  }), { status: 401 }));

  render(<App />);
  expect(screen.getByRole("heading", { name: "Dashboard" })).toBeTruthy();

  await act(async () => {
    await expect(apiRequest("/auth/me")).rejects.toMatchObject({ status: 401 });
  });

  await waitFor(() => expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy());
});

test("clears the in-memory session when the user signs out", async () => {
  await act(async () => setAccessToken("active-access-token"));
  window.history.pushState({}, "", "/dashboard");

  render(<App />);
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeTruthy();
});
