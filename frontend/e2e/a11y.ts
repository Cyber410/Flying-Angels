import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21aa"];
const BLOCKING_IMPACTS = new Set(["serious", "critical"]);

// Scan the current page with axe and fail only on serious or critical WCAG 2.1 AA violations.
// The check is soft, so the rest of the test still runs and reports its own failures.
export async function expectNoSeriousA11yViolations(page: Page, label = "page") {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  const blocking = results.violations.filter(v => v.impact && BLOCKING_IMPACTS.has(v.impact));

  const lines = blocking.flatMap(v =>
    v.nodes.map(node => `  [axe] ${v.id} | ${v.impact} | ${node.target.join(" ")}`));
  if (lines.length) {
    console.log(`axe violations on ${label} (${page.url()}):\n${lines.join("\n")}`);
    await test.info().attach(`axe-${label}`, {
      body: JSON.stringify(blocking.map(v => ({ id: v.id, impact: v.impact, help: v.help, targets: v.nodes.map(n => n.target) })), null, 2),
      contentType: "application/json",
    });
  }

  expect.soft(lines, `Serious/critical axe violations on ${label}:\n${lines.join("\n")}`).toEqual([]);
}
