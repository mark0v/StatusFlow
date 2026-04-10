import { expect, test } from "@playwright/test";

const uniqueSuffix = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

test.describe("StatusFlow operator console", () => {
  test("renders static status summary cards", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByAltText("StatusFlow operator console")).toBeVisible();
    await expect(page.getByText("Cancelled").locator("..")).toContainText("0");

    const summaryCards = page.locator(".summary-strip .status-card");
    await expect(summaryCards).toHaveCount(6);
  });

  test("closes the status filter when clicking outside", async ({ page }) => {
    await page.goto("/");

    const filterButton = page.getByRole("button", { name: "Filter" });
    await filterButton.click();

    await expect(page.getByLabel("Cancelled")).toBeVisible();
    await page.mouse.click(20, 20);
    await expect(page.getByLabel("Cancelled")).toBeHidden();
  });

  test("creates an order and transitions it to in review", async ({ page }) => {
    const orderTitle = `E2E order ${uniqueSuffix()}`;

    await page.goto("/");

    await page.getByRole("button", { name: "Create order" }).click();
    await page.getByLabel("Order title").fill(orderTitle);
    await page
      .getByLabel("Description")
      .fill("Created by Playwright against the live operator console.");
    await page.getByRole("button", { name: /^Create order$/ }).click();

    const createdRow = page.locator("tbody tr").filter({ hasText: orderTitle }).first();
    await expect(createdRow).toBeVisible();
    await expect(createdRow).toContainText("New");

    await createdRow.getByRole("button", { name: "Change status" }).click();
    await page.getByRole("button", { name: "In review" }).click();

    await expect(createdRow).toContainText("In review");
  });
});
