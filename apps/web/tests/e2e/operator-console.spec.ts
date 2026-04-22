import { expect, test, type Page } from "@playwright/test";

const uniqueSuffix = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

async function signIn(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Sign in with email instead" }).click();
  await page.getByLabel("Email").fill("operator@example.com");
  await page.getByLabel("Password").fill("operator123");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Active orders" })).toBeVisible();
}

test.describe("StatusFlow operator console", () => {
  test("exposes browser branding metadata", async ({ page }) => {
    await page.goto("/");

    await expect(page).toHaveTitle("StatusFlow Operator Console");

    const faviconHref = await page.locator('link[rel="icon"]').getAttribute("href");
    const manifestHref = await page.locator('link[rel="manifest"]').getAttribute("href");
    const themeColor = await page.locator('meta[name="theme-color"]').getAttribute("content");

    expect(faviconHref).toBe("/favicon.svg");
    expect(manifestHref).toBe("/manifest.webmanifest");
    expect(themeColor).toBe("#09111f");
  });

  test("renders static status summary cards", async ({ page }) => {
    await signIn(page);

    await expect(page.getByAltText("StatusFlow operator console")).toBeVisible();
    await expect(page.locator(".summary-strip .status-card").filter({ hasText: "Cancelled" })).toBeVisible();

    const summaryCards = page.locator(".summary-strip .status-card");
    await expect(summaryCards).toHaveCount(7);
  });

  test("filters the queue through status summary cards", async ({ page }) => {
    await signIn(page);

    await page.locator(".summary-strip .status-card").filter({ hasText: "In review" }).click();

    await expect(page.locator("tbody tr").first()).toContainText("In review");
    await expect(page.locator("tbody tr").filter({ hasNotText: "In review" })).toHaveCount(0);

    await page.locator(".summary-strip .status-card").filter({ hasText: "Total" }).click();

    await expect(page.locator("tbody tr").first()).toBeVisible();
  });

  test("creates an order and transitions it to in review", async ({ page }) => {
    const orderTitle = `E2E order ${uniqueSuffix()}`;

    await signIn(page);

    await page.getByRole("button", { name: "Create order" }).click();
    await page.getByLabel("Order title").fill(orderTitle);
    await page
      .getByLabel("Description")
      .fill("Created by Playwright against the live operator console.");
    await page.getByRole("button", { name: /^Create order$/ }).click();

    const createdRow = page.locator("tbody tr").filter({ hasText: orderTitle }).first();
    await expect(createdRow).toBeVisible();
    await expect(createdRow).toContainText("New");

    await createdRow.click();
    await page.getByRole("button", { name: "Change status" }).click();
    await page.locator(".row-dropdown").getByRole("button", { name: "In review" }).click();

    await expect(createdRow).toContainText("In review");
  });

  test("shows history and posts a live comment in the inspector", async ({ page }) => {
    const orderTitle = `Inspector order ${uniqueSuffix()}`;
    const commentBody = `Live note ${uniqueSuffix()}`;

    await signIn(page);

    await page.getByRole("button", { name: "Create order" }).click();
    await page.getByLabel("Order title").fill(orderTitle);
    await page.getByLabel("Description").fill("Created to verify comments and history in the inspector.");
    await page.getByRole("button", { name: /^Create order$/ }).click();

    const createdRow = page.locator("tbody tr").filter({ hasText: orderTitle }).first();
    await expect(createdRow).toBeVisible();

    await createdRow.click();
    await page.getByRole("tab", { name: /History/ }).click();
    await expect(page.getByText("Created in New")).toBeVisible();

    await page.getByRole("tab", { name: /Comments/ }).click();
    await page.getByLabel("Add a comment").fill(commentBody);
    await page.getByRole("button", { name: "Add comment" }).click();

    await expect(page.getByText(commentBody)).toBeVisible();
  });

  test("closes inspector status actions when clicking outside", async ({ page }) => {
    await signIn(page);

    const firstRow = page.locator("tbody tr").first();
    await firstRow.click();
    await page.getByRole("button", { name: "Change status" }).click();

    const actionMenu = page.locator(".row-dropdown").first();
    await expect(actionMenu).toBeVisible();

    await page.mouse.click(20, 20);
    await expect(actionMenu).toBeHidden();
  });
});
