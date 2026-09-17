import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { LocalizationProvider } from "../localization/LocalizationProvider";
import { LocaleSwitcher } from "../localization/LocaleSwitcher";
import { LocalizedDatePicker } from "./LocalizedDatePicker";

afterEach(() => localStorage.clear());

test("updates month names, weekday abbreviations, and calendar controls after locale switching", () => {
  const onChange = vi.fn();
  render(<LocalizationProvider><LocaleSwitcher /><label htmlFor="month">Месяц</label><LocalizedDatePicker id="month" onChange={onChange} type="month" value="2026-09" /></LocalizationProvider>);

  fireEvent.click(screen.getByLabelText("Месяц"));
  expect(screen.getByRole("dialog", { name: "Календарь" }).textContent).toContain("сентябрь 2026 г.");
  expect(screen.getByRole("button", { name: "Следующий месяц" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "сент." })).toBeTruthy();

  fireEvent.change(screen.getByRole("combobox", { name: "Язык" }), { target: { value: "en" } });
  expect(screen.getByRole("dialog", { name: "Calendar" }).textContent).toContain("September 2026");
  expect(screen.getByRole("button", { name: "Next month" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Sep" })).toBeTruthy();
});

test("returns ISO date value selected in the localized calendar", () => {
  const onChange = vi.fn();
  render(<LocalizationProvider><label htmlFor="date">Дата</label><LocalizedDatePicker id="date" onChange={onChange} type="date" value="2026-09-01" /></LocalizationProvider>);

  fireEvent.click(screen.getByLabelText("Дата"));
  fireEvent.click(screen.getByRole("button", { name: "17 сентября 2026 г." }));

  expect(onChange).toHaveBeenCalledWith("2026-09-17");
});

test("clears a selected date and closes the calendar with Escape", () => {
  const onChange = vi.fn();
  render(<LocalizationProvider><label htmlFor="date">Дата</label><LocalizedDatePicker id="date" onChange={onChange} type="date" value="2026-09-01" /></LocalizationProvider>);

  fireEvent.click(screen.getByLabelText("Дата"));
  fireEvent.keyDown(screen.getByRole("dialog", { name: "Календарь" }), { key: "Escape" });
  expect(screen.queryByRole("dialog", { name: "Календарь" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Очистить дату" }));

  expect(onChange).toHaveBeenCalledWith("");
});
