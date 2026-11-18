import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";

afterEach(/* Clean up rendered content and mocks after each test. */ () => vi.unstubAllGlobals());
describe("authenticated API requests", /* Group the authenticated API requests checks. */ () => {
  it("obtains a fresh CSRF token before a write and preserves the JSON body", /* Verify that obtains a fresh CSRF token before a write and preserves the JSON body. */ async () => {
    const body = JSON.stringify({ athleteId: 7 });
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "fresh" })))
      .mockResolvedValueOnce(new Response("{}"));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch("/api/events/1/participants", { method: "POST", headers: { "Content-Type": "application/json" }, body });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/csrf");
    const [url, options] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/events/1/participants");
    expect(options).toMatchObject({ body, credentials: "same-origin", method: "POST" });
    expect((options.headers as Headers).get("X-CSRF-TOKEN")).toBe("fresh");
    expect((options.headers as Headers).get("Content-Type")).toBe("application/json");
  });

  it("keeps multipart boundaries under browser control", /* Verify that keeps multipart boundaries under browser control. */ async () => {
    const body = new FormData(); body.append("file", new File(["sample"], "athletes.csv"));
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "fresh" }))).mockResolvedValueOnce(new Response("{}"));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch("/api/athletes/import/preview", { method: "POST", body });
    expect(fetchMock.mock.calls[1][1].body).toBe(body);
    expect((fetchMock.mock.calls[1][1].headers as Headers).has("Content-Type")).toBe(false);
  });

  it("does not send a write if CSRF preparation fails", /* Verify that does not send a write if CSRF preparation fails. */ async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("", { status: 503 })); vi.stubGlobal("fetch", fetchMock);
    await expect(apiFetch("/api/users", { method: "POST" })).rejects.toThrow("Unable to prepare a secure request");
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("notifies the session provider about expired protected requests", /* Verify that notifies the session provider about expired protected requests. */ async () => {
    const listener = vi.fn(); window.addEventListener("academy:session-expired", listener);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
    try { await apiFetch("/api/athletes"); expect(listener).toHaveBeenCalledOnce(); }
    finally { window.removeEventListener("academy:session-expired", listener); }
  });
});
