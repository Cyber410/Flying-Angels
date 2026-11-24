import { defineConfig, devices } from "@playwright/test";

// Live smoke tests run in a real browser against an already-deployed app at BASE_URL.
// Nothing is mocked and no dev server is started; the tests only read data.
// Credentials and the URL come from environment variables (see e2e-live/auth.ts).
export default defineConfig({
  testDir: "./e2e-live",
  fullyParallel: false,
  retries: 1,
  workers: 1,
  outputDir: "./test-results-live",
  reporter: [["html", { open: "never", outputFolder: "playwright-report-live" }], ["list"]],
  use: {
    baseURL: process.env.BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 375, height: 667 } } },
  ],
});
