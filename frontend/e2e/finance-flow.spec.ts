import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

function currentMonth(): string {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
}

async function selectDate(page: Page, inputId: string, value: string): Promise<void> {
  const date = new Date(`${value}T00:00:00Z`);
  const label = new Intl.DateTimeFormat("en-US", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(date);

  await page.locator(`#${inputId}`).click();
  for (let month = 0; month < date.getUTCMonth(); month += 1) {
    await page.getByRole("dialog", { name: "Calendar" }).getByRole("button", { name: "Next month" }).click();
  }
  await page.getByRole("dialog", { name: "Calendar" }).getByRole("button", { name: label }).click();
}

async function selectMonth(page: Page, inputId: string, value: string): Promise<void> {
  const date = new Date(`${value}-01T00:00:00Z`);
  const label = new Intl.DateTimeFormat("en-US", { month: "short", timeZone: "UTC" }).format(date);

  await page.locator(`#${inputId}`).click();
  await page.getByRole("dialog", { name: "Calendar" }).getByRole("button", { name: label, exact: true }).click();
}

test("switches locale and validates an empty registration form", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("html")).toHaveAttribute("lang", "ru");
  await page.getByLabel("Язык").selectOption("en");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();

  await page.getByRole("link", { name: "Create account" }).click();
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("alert").first()).toBeVisible();
});

test("registers, signs in, and manages a transaction through CSV import", async ({ page }, testInfo) => {
  const suffix = crypto.randomUUID();
  const email = `e2e-${suffix}@example.test`;
  const password = "E2eTestPassword!2026";
  const categoryName = `E2E groceries ${suffix}`;
  const updatedCategoryName = `Updated ${categoryName}`;
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
  await page.getByRole("button", { name: `Edit ${categoryName}` }).click();
  await page.getByLabel("Name").fill(updatedCategoryName);
  await page.getByRole("button", { name: "Save category" }).click();
  await expect(page.getByText(updatedCategoryName, { exact: true })).toBeVisible();

  await page.getByRole("link", { name: "Transactions" }).click();
  await page.getByRole("button", { name: "Add transaction" }).click();
  await page.locator("#transaction-category").selectOption({ label: `🏠 ${updatedCategoryName}` });
  await page.locator("#transaction-amount").fill("42.50");
  await page.locator("#transaction-currency").fill("USD");
  await page.locator("#transaction-exchangeRateToBase").fill("1");
  await selectDate(page, "transaction-transactionDate", `${month}-15`);
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

  await page.getByRole("button", { name: "Dismiss notification" }).last().click();
  await page.getByRole("button", { name: "Edit transaction" }).click();
  await page.locator("#transaction-description").fill("Updated after notification E2E grocery transaction");
  await page.getByRole("button", { name: "Save transaction" }).click();
  await expect(page.getByText("Updated after notification E2E grocery transaction")).toBeVisible();

  await page.getByRole("link", { name: "Budgets" }).click();
  await page.getByRole("button", { name: "Add budget" }).click();
  await page.getByLabel("Expense category").selectOption({ label: `🏠 ${updatedCategoryName}` });
  await selectMonth(page, "budget-budgetMonth", month);
  await page.getByLabel("Limit amount").fill("100");
  const budgetResponse = page.waitForResponse("**/api/v1/budgets");
  await page.getByRole("button", { name: "Save budget" }).click();
  expect((await budgetResponse).status()).toBe(201);
  const budgetProgress = page.getByRole("progressbar", { name: "Budget progress" });
  await expect(budgetProgress).toBeVisible();
  await expect(budgetProgress).toHaveAttribute("aria-valuetext", /Spent 42\.5/);

  await page.getByRole("link", { name: "Dashboard" }).click();
  await selectMonth(page, "dashboard-month-picker", month);
  const dashboardCategory = page.getByRole("listitem").filter({ hasText: updatedCategoryName }).first();
  await expect(dashboardCategory).toBeVisible();
  await expect(dashboardCategory).toContainText("42.5");

  await page.getByRole("link", { name: "Recurring" }).click();
  await page.getByRole("button", { name: "Add recurring rule" }).click();
  await page.locator("#recurring-categoryId").selectOption({ label: `🏠 ${updatedCategoryName}` });
  await page.locator("#recurring-amount").fill("10");
  await page.locator("#recurring-currency").fill("USD");
  await page.locator("#recurring-exchangeRateToBase").fill("1");
  await page.locator("#recurring-dayOfMonth").fill("15");
  await selectDate(page, "recurring-startDate", `${month}-15`);
  await page.locator("#recurring-description").fill("E2E recurring rule");
  await page.getByRole("button", { name: "Save rule" }).click();
  await expect(page.getByText("Recurring rule created.")).toBeVisible();
  await page.getByRole("button", { name: "Pause rule" }).click();
  await expect(page.getByText("Recurring rule paused.")).toBeVisible();
  await page.getByRole("button", { name: "Activate rule" }).click();
  await expect(page.getByText("Recurring rule activated.")).toBeVisible();

  await page.getByRole("link", { name: "Audit log" }).click();
  await page.getByLabel("Resource type").selectOption("TRANSACTION");
  await page.getByRole("button", { name: "Apply filters" }).click();
  await expect(page.getByText("Created").first()).toBeVisible();
  await expect(page.getByText("Updated").first()).toBeVisible();

  await page.getByRole("link", { name: "Transactions" }).click();
  await page.getByLabel("Minimum amount").fill("40");
  await page.getByLabel("Maximum amount").fill("50");
  await page.getByRole("button", { name: "Apply filters" }).click();
  await expect(page.getByText("Updated after notification E2E grocery transaction")).toBeVisible();
  await page.getByRole("button", { name: "Reset filters" }).click();
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

  await page.getByLabel("CSV file").setInputFiles({
    name: "invalid-header.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("amount,amount\n1,2\n"),
  });
  await expect(page.getByText("The CSV header is invalid or too long.")).toBeVisible();

  const dismissButtons = page.getByRole("button", { name: "Dismiss notification" });
  while (await dismissButtons.count() > 0) {
    await dismissButtons.last().click();
  }
  await page.getByRole("button", { name: "Delete transaction" }).first().click();
  await expect(page.getByRole("dialog", { name: "Delete transaction?" })).toBeVisible();
  await page.getByRole("dialog", { name: "Delete transaction?" }).getByRole("button", { name: "Delete transaction" }).click();
  await expect(page.getByText("Transaction deleted.")).toBeVisible();
});
