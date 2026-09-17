import { expect, test } from "@playwright/test";

function currentMonth(): string {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
}

test("registers, signs in, and manages a transaction through CSV import", async ({ page }, testInfo) => {
  const suffix = crypto.randomUUID();
  const email = `e2e-${suffix}@example.test`;
  const password = "E2eTestPassword!2026";
  const categoryName = `E2E groceries ${suffix}`;
  const month = currentMonth();

  await page.addInitScript(() => localStorage.setItem("finance-tracker.locale", "en"));
  await page.goto("/register");
  await page.getByLabel("Name").fill("E2E Test User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByLabel("Base currency").fill("USD");
  const registrationResponse = page.waitForResponse("**/api/v1/auth/register");
  await page.getByRole("button", { name: "Create account" }).click();
  expect((await registrationResponse).status()).toBe(201);
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  const loginResponse = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: "Sign in" }).click();
  expect((await loginResponse).status()).toBe(200);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.getByRole("link", { name: "Categories" }).click();
  await page.getByRole("button", { name: "Add category" }).click();
  await page.getByLabel("Name").fill(categoryName);
  const categoryResponse = page.waitForResponse("**/api/v1/categories");
  await page.getByRole("button", { name: "Save category" }).click();
  expect((await categoryResponse).status()).toBe(201);
  await expect(page.getByText(categoryName, { exact: true })).toBeVisible();

  await page.getByRole("link", { name: "Transactions" }).click();
  await page.getByRole("button", { name: "Add transaction" }).click();
  await page.locator("#transaction-category").selectOption({ label: `🏠 ${categoryName}` });
  await page.locator("#transaction-amount").fill("42.50");
  await page.locator("#transaction-currency").fill("USD");
  await page.locator("#transaction-exchangeRateToBase").fill("1");
  await page.locator("#transaction-transactionDate").fill(`${month}-15`);
  await page.locator("#transaction-description").fill("E2E grocery transaction");
  const transactionResponse = page.waitForResponse("**/api/v1/transactions");
  await page.getByRole("button", { name: "Save transaction" }).click();
  expect((await transactionResponse).status()).toBe(201);
  await expect(page.getByText("E2E grocery transaction")).toBeVisible();

  await page.getByRole("button", { name: "Edit transaction" }).click();
  await page.locator("#transaction-description").fill("Updated E2E grocery transaction");
  const transactionUpdateResponse = page.waitForResponse((response) => /\/api\/v1\/transactions\/[^/]+$/.test(response.url()) && response.request().method() === "PATCH");
  await page.getByRole("button", { name: "Save transaction" }).click();
  expect((await transactionUpdateResponse).status()).toBe(200);
  await expect(page.getByText("Updated E2E grocery transaction")).toBeVisible();

  await page.getByRole("link", { name: "Budgets" }).click();
  await page.getByRole("button", { name: "Add budget" }).click();
  await page.getByLabel("Expense category").selectOption({ label: `🏠 ${categoryName}` });
  await page.getByLabel("Month").last().fill(month);
  await page.getByLabel("Limit amount").fill("100");
  const budgetResponse = page.waitForResponse("**/api/v1/budgets");
  await page.getByRole("button", { name: "Save budget" }).click();
  expect((await budgetResponse).status()).toBe(201);
  const budgetProgress = page.getByRole("progressbar", { name: "Budget progress" });
  await expect(budgetProgress).toBeVisible();
  await expect(budgetProgress).toHaveAttribute("aria-valuetext", /Spent 42\.5/);

  await page.getByRole("link", { name: "Dashboard" }).click();
  await page.locator("#dashboard-month-picker").fill(month);
  const dashboardCategory = page.getByRole("listitem").filter({ hasText: categoryName }).first();
  await expect(dashboardCategory).toBeVisible();
  await expect(dashboardCategory).toContainText("42.5");

  await page.getByRole("link", { name: "Transactions" }).click();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export CSV" }).click();
  const download = await downloadPromise;
  await expect(download.suggestedFilename()).toBe("transactions.csv");
  const csvPath = testInfo.outputPath("transactions.csv");
  await download.saveAs(csvPath);

  await page.getByLabel("CSV file").setInputFiles(csvPath);
  const previewResponse = page.waitForResponse("**/api/v1/transactions/imports/preview");
  await page.getByRole("button", { name: "Preview import" }).click();
  expect((await previewResponse).status()).toBe(200);
  await expect(page.getByRole("heading", { name: "Preview rows" })).toBeVisible();
  const importResponse = page.waitForResponse("**/api/v1/transactions/imports");
  await page.getByRole("button", { name: "Confirm import" }).click();
  expect((await importResponse).status()).toBe(201);
  await expect(page.getByText("1 transaction imported.")).toBeVisible();
});
