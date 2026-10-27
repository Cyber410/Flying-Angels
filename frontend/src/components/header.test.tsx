import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import Header from "./header";

describe("shared navigation", () => {
  it.each(["/", "/athletes/7"])("keeps Athletes active at %s", (path) => {
    render(<MemoryRouter initialEntries={[path]}><Header /></MemoryRouter>);
    expect(screen.getByRole("link", { name: "Athletes" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "FLYING ANGELS" })).toHaveAttribute("href", "/");
    expect(screen.queryByText("FA")).not.toBeInTheDocument();
  });

  it("navigates between Meets, Results, and the athlete homepage", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <Header />
        <Routes>
          <Route path="/" element={<p>Athlete homepage</p>} />
          <Route path="/meets" element={<p>Meets destination</p>} />
          <Route path="/results" element={<p>Results destination</p>} />
        </Routes>
      </MemoryRouter>
    );
    const nav = within(screen.getByRole("navigation", { name: "Main navigation" }));
    await user.click(nav.getByRole("link", { name: "Meets" }));
    expect(screen.getByText("Meets destination")).toBeInTheDocument();
    expect(nav.getByRole("link", { name: "Meets" })).toHaveAttribute("aria-current", "page");
    await user.click(nav.getByRole("link", { name: "Results" }));
    expect(screen.getByText("Results destination")).toBeInTheDocument();
    await user.click(nav.getByRole("link", { name: "Athletes" }));
    expect(screen.getByText("Athlete homepage")).toBeInTheDocument();
  });

  it("shows Import as disabled without linking to an unfinished route", () => {
    render(<MemoryRouter><Header /></MemoryRouter>);
    const link = screen.getByRole("link", { name: "Import" });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(link).not.toHaveAttribute("href");
  });
});
