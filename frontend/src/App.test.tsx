import { act, render, screen } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { App } from "./App";
import { setAccessToken } from "./shared/api/accessToken";

afterEach(async () => {
  await act(async () => setAccessToken(null));
  window.history.pushState({}, "", "/");
});

test("redirects an unauthenticated protected route to sign in", () => {
  window.history.pushState({}, "", "/dashboard");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Sign in" })).toBeTruthy();
});

test("renders the public registration placeholder", () => {
  window.history.pushState({}, "", "/register");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Create your account" })).toBeTruthy();
});

test("renders a protected route when an in-memory token is available", async () => {
  await act(async () => setAccessToken("access-token"));
  window.history.pushState({}, "", "/dashboard");

  render(<App />);

  expect(screen.getByRole("heading", { name: "Dashboard" })).toBeTruthy();
  expect(screen.getByRole("navigation", { name: "Primary navigation" })).toBeTruthy();
});
