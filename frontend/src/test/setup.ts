import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Simulate the native dialog API while testing confirmation flows in jsdom.
Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value: /* Mark the test dialog as open without a native browser. */ function (this: HTMLDialogElement) { this.open = true; } });
Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value: /* Mark the test dialog as closed without a native browser. */ function (this: HTMLDialogElement) { this.open = false; } });

// Remove the rendered page after every test so tests cannot affect each other.
afterEach(/* Clean up rendered content and mocks after each test. */ () => {
  cleanup();
});
