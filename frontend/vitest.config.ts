import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.ts";

// Test settings live here so the app's vite.config.ts stays untouched.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      // jsdom is a simulated browser, so components can render without Chrome.
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      css: false,
      restoreMocks: true,
    },
  })
);
