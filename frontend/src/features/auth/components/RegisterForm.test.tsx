import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { App } from "../../../App";

afterEach(() => {
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

test("shows client validation errors before submitting registration details", async () => {
  window.history.pushState({}, "", "/register");
  const fetchMock = vi.spyOn(globalThis, "fetch");

  render(<App />);
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));

  expect(await screen.findByText("Enter your name.")).toBeTruthy();
  expect(screen.getByText("Enter your email address.")).toBeTruthy();
  expect(screen.getByText("Use at least 12 characters.")).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});

test("creates an account and returns to sign in", async () => {
  window.history.pushState({}, "", "/register");
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 201 }));

  render(<App />);
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Test User" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-for-testing" } });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));

  expect(await screen.findByText("Your account has been created. You can now sign in.")).toBeTruthy();
  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeTruthy();
  expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
    displayName: "Test User", email: "person@example.test", password: "password-for-testing", baseCurrency: "USD",
  });
});

test("accepts a backend-supported currency outside the common defaults", async () => {
  window.history.pushState({}, "", "/register");
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 201 }));

  render(<App />);
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Test User" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-for-testing" } });
  fireEvent.change(screen.getByLabelText("Base currency"), { target: { value: "GBP" } });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));

  await screen.findByRole("heading", { name: "Sign in" });
  expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ baseCurrency: "GBP" });
});

test("shows a retry-later message when registration is rate limited", async () => {
  window.history.pushState({}, "", "/register");
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
    code: "RATE_LIMITED", message: "Too many requests.", traceId: null, violations: [],
  }), { status: 429 }));

  render(<App />);
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Test User" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-for-testing" } });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));

  expect(await screen.findByText("Too many attempts. Please try again later.")).toBeTruthy();
});

test("rejects a password that exceeds the backend UTF-8 byte limit", async () => {
  window.history.pushState({}, "", "/register");

  render(<App />);
  fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Test User" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "person@example.test" } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: "🙂".repeat(20) } });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));

  expect(await screen.findByText("Use no more than 72 bytes.")).toBeTruthy();
});
