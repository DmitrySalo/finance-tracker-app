import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { LocalizationProvider } from "./LocalizationProvider";

afterEach(() => {
  localStorage.clear();
  document.documentElement.lang = "";
});

test("uses Russian by default and persists a selected language", () => {
  render(<LocalizationProvider><LocaleSwitcher /></LocalizationProvider>);

  const switcher = screen.getByRole<HTMLSelectElement>("combobox", { name: "Язык" });
  expect(switcher.value).toBe("ru");
  expect(document.documentElement.lang).toBe("ru");

  fireEvent.change(switcher, { target: { value: "en" } });
  expect(localStorage.getItem("finance-tracker.locale")).toBe("en");
  expect(document.documentElement.lang).toBe("en");
});

test("restores a valid persisted language", () => {
  localStorage.setItem("finance-tracker.locale", "en");
  render(<LocalizationProvider><LocaleSwitcher /></LocalizationProvider>);
  expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Language" }).value).toBe("en");
});
