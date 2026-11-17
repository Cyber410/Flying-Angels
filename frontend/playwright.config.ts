import { defineConfig, devices } from "@playwright/test";

// End-to-end tests run in a real browser against the Vite dev server.
// API calls are mocked inside each test, so the backend and database
// do not need to be running.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  retries: 0,
 reporter: [["html", { open: "never" }], ["list"]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev -- --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: true,
   timeout: 120_000,
  },
});