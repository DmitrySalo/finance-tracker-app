import { render } from "@testing-library/react";
import { expect, test } from "vitest";
import { App } from "./App";

test("renders the initial empty application shell", () => {
  const { container } = render(<App />);

  expect(container.childElementCount).toBe(0);
});
