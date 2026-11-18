import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import RequireSession from "./RequireSession";
import { AuthContext } from "./authContext";
import type { AuthState } from "./authContext";

// Exercise route access with explicit session identities and no network requests.
function renderRoute(role: "ADMIN" | "COACH" | "ATHLETE" | null, permissions: string[]) {
  const value: AuthState = { session: { authenticationRequired: true, user: role ? { id: 1, email: "fictional@example.test", name: "Test", role, active: true, athleteId: role === "ATHLETE" ? 7 : null, accountStatus: "ACTIVE" } : null, permissions }, loading: false, error: "", refresh: vi.fn(), login: vi.fn(), logout: vi.fn(), can: /* Provide the can mock implementation. */ permission => permissions.includes(permission) };
  render(<AuthContext.Provider value={value}><MemoryRouter initialEntries={["/users"]}><Routes>
    <Route element={<RequireSession permission="MANAGE_USERS" />}><Route path="/users" element={<p>User management</p>} /></Route>
    <Route path="/login" element={<p>Login destination</p>} /><Route path="/portal" element={<p>Athlete destination</p>} /><Route path="/" element={<p>Staff destination</p>} />
  </Routes></MemoryRouter></AuthContext.Provider>);
}

describe("session route guards", /* Group the session route guards checks. */ () => {
  it("redirects anonymous users to login", /* Verify that redirects anonymous users to login. */ () => { renderRoute(null, []); expect(screen.getByText("Login destination")).toBeInTheDocument(); });
  it("redirects athletes away from staff pages", /* Verify that redirects athletes away from staff pages. */ () => { renderRoute("ATHLETE", []); expect(screen.getByText("Athlete destination")).toBeInTheDocument(); });
  it("keeps coaches out of account administration", /* Verify that keeps coaches out of account administration. */ () => { renderRoute("COACH", ["READ_STAFF"]); expect(screen.getByText("Staff destination")).toBeInTheDocument(); });
  it("allows administrators with the required capability", /* Verify that allows administrators with the required capability. */ () => { renderRoute("ADMIN", ["MANAGE_USERS"]); expect(screen.getByText("User management")).toBeInTheDocument(); });
});
