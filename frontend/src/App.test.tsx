import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { App } from "./App";
import { setAccessToken } from "./shared/api/accessToken";

afterEach(async () => {
  await act(async () => setAccessToken(null));
  localStorage.clear();
  window.history.pushState({}, "", "/");
});

test("redirects an unauthenticated protected route to sign in", () => {
  localStorage.setItem("finance-tracker.locale", "en");
  window.history.pushState({}, "", "/dashboard");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy();
});

test("renders the public registration placeholder", () => {
  localStorage.setItem("finance-tracker.locale", "en");
  window.history.pushState({}, "", "/register");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Create your account" })).toBeTruthy();
});

test("renders a protected route when an in-memory token is available", async () => {
  localStorage.setItem("finance-tracker.locale", "en");
  await act(async () => setAccessToken("access-token"));
  window.history.pushState({}, "", "/dashboard");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Dashboard" })).toBeTruthy();
  expect(screen.getByRole("navigation", { name: "Primary navigation" })).toBeTruthy();
});

test("switches the public sign-in page from Russian to English and persists the selection", () => {
  window.history.pushState({}, "", "/login");
  render(<App />);

  expect(screen.getByRole("heading", { name: "Войти" })).toBeTruthy();
  const switcher = screen.getByRole<HTMLSelectElement>("combobox", { name: "Язык" });
  fireEvent.change(switcher, { target: { value: "en" } });

  expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy();
  expect(document.documentElement.lang).toBe("en");
  expect(localStorage.getItem("finance-tracker.locale")).toBe("en");
});

test("shows the language switcher in the authenticated application shell", async () => {
  await act(async () => setAccessToken("access-token"));
  window.history.pushState({}, "", "/dashboard");
  render(<App />);

  expect(screen.getByRole("combobox", { name: "Язык" })).toBeTruthy();
});
