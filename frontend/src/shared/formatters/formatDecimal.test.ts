import { expect, test } from "vitest";
import { formatDecimal } from "./formatDecimal";

test("formats decimal strings with exactly two fractional digits", () => {
  expect(formatDecimal("40.000000000000")).toBe("40.00");
  expect(formatDecimal("12.3400")).toBe("12.34");
  expect(formatDecimal("7")).toBe("7.00");
});

test("rounds decimal strings without losing integer precision", () => {
  expect(formatDecimal("12.3450")).toBe("12.35");
  expect(formatDecimal("999999999999999.9999")).toBe("1000000000000000.00");
  expect(formatDecimal("-0.0001")).toBe("0.00");
  expect(formatDecimal("-9.995")).toBe("-10.00");
});

test("keeps an invalid decimal string unchanged", () => {
  expect(formatDecimal("not-a-number")).toBe("not-a-number");
});
