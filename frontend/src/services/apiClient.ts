// Uses session cookies and obtains a current CSRF token before each write.
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const csrf = await fetch("/api/csrf", { credentials: "same-origin", cache: "no-store", signal: init.signal });
    if (!csrf.ok) throw new Error("Unable to prepare a secure request. Reload the page and try again.");
    const text = await csrf.text();
    const token: { headerName?: string; token?: string } | null = text ? JSON.parse(text) : null;
    if (token?.headerName && token.token) headers.set(token.headerName, token.token);
  }
  const response = await fetch(input, { ...init, headers, credentials: "same-origin" });
  if (response.status === 401 && input !== "/api/auth/login" && input !== "/api/auth/me") {
    window.dispatchEvent(new Event("academy:session-expired"));
  }
  return response;
}
