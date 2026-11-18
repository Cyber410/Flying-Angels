import { createContext, useContext } from "react";
import type { Session } from "../types/auth";

export type AuthState = {
  session: Session | null; loading: boolean; error: string;
  refresh: () => Promise<void>; login: (email: string, password: string) => Promise<Session>;
  logout: () => Promise<void>; can: (permission: string) => boolean;
};
export const AuthContext = createContext<AuthState | null>(null);

// Expose the current session and permission checks to pages and shared controls.
export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error("Authentication context is unavailable.");
  return context;
}

// Let isolated legacy component tests render without fabricating a live session.
export function useOptionalAuth(): AuthState | null {
  return useContext(AuthContext);
}
