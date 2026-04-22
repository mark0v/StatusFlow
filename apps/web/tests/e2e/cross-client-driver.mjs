import { chromium, expect } from "@playwright/test";

function parseArgs(argv) {
  const parsed = {};

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) {
      continue;
    }

    const key = token.slice(2);
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) {
      throw new Error(`Missing value for argument --${key}`);
    }

    parsed[key] = value;
    index += 1;
  }

  return parsed;
}

function getRequiredArg(args, key) {
  const value = args[key];
  if (!value) {
    throw new Error(`Missing required argument --${key}`);
  }

  return value;
}

function getStatusLabel(status) {
  switch (status) {
    case "in_review":
      return "In review";
    case "fulfilled":
      return "Fulfilled";
    case "cancelled":
      return "Cancelled";
    case "new":
      return "New";
    default:
      throw new Error(`Unsupported status label mapping for ${status}`);
  }
}

async function signIn(page, baseUrl) {
  await page.goto(baseUrl);
  await page.getByRole("button", { name: "Sign in with email instead" }).click();
  await page.getByLabel("Email").fill("operator@example.com");
  await page.getByLabel("Password").fill("operator123");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Active orders" })).toBeVisible();
}

async function createAndMutateOrder(page, title, description, comment, statusLabel) {
  await page.getByRole("button", { name: "Create order" }).click();
  await page.getByLabel("Order title").fill(title);
  await page.getByLabel("Description").fill(description);
  await page.getByRole("button", { name: /^Create order$/ }).click();

  const createdRow = page.locator("tbody tr").filter({ hasText: title }).first();
  await expect(createdRow).toBeVisible();

  await createdRow.click();
  await page.getByRole("button", { name: "Change status" }).click();
  await page.locator(".row-dropdown").getByRole("button", { name: statusLabel }).click();
  await expect(createdRow).toContainText(statusLabel);

  await page.getByRole("tab", { name: /Comments/ }).click();
  await page.getByLabel("Add a comment").fill(comment);
  await page.getByRole("button", { name: "Add comment" }).click();
  await expect(page.getByText(comment)).toBeVisible();
}

async function verifyOrder(page, title, comment, statusLabel) {
  // Wait for the search input to exist and be visible (with scroll into view)
  const searchInput = page.locator("input[placeholder='Search code, title, or customer']");
  await searchInput.waitFor({ state: "attached", timeout: 15000 });
  await searchInput.scrollIntoViewIfNeeded({ timeout: 5000 });
  await searchInput.fill(title);

  const matchingRow = page.locator("tbody tr").filter({ hasText: title }).first();
  await expect(matchingRow).toBeVisible();
  await expect(matchingRow).toContainText(statusLabel);

  await matchingRow.click();
  await page.getByRole("tab", { name: /Comments/ }).click();
  await expect(page.getByText(comment)).toBeVisible();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const mode = getRequiredArg(args, "mode");
  const title = getRequiredArg(args, "title");
  const comment = getRequiredArg(args, "comment");
  const status = args.status ?? "in_review";
  const description = args.description ?? "Cross-client driver note.";
  const screenshotPath = args["screenshot-path"];
  const baseUrl = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";
  const statusLabel = getStatusLabel(status);

  const browser = await chromium.launch({ headless: true });

  try {
    const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
    await signIn(page, baseUrl);

    if (mode === "create-and-mutate") {
      await createAndMutateOrder(page, title, description, comment, statusLabel);
    } else if (mode === "verify-order") {
      await verifyOrder(page, title, comment, statusLabel);
    } else {
      throw new Error(`Unsupported mode: ${mode}`);
    }

    if (screenshotPath) {
      await page.screenshot({ path: screenshotPath, fullPage: true });
    }

    process.stdout.write(
      `${JSON.stringify({
        mode,
        title,
        comment,
        status,
        statusLabel,
        screenshotPath: screenshotPath ?? null
      })}\n`
    );
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
