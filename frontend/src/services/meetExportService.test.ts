import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";
import { downloadMeetExport, fileNameFromDisposition, getMeetExportOptions } from "./meetExportService";

vi.mock("./apiClient");
// Start transport checks with no outstanding request or browser-download mocks.
beforeEach(() => vi.resetAllMocks());
// Restore browser primitives after testing object-URL downloads.
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

// Keep exports on the same authenticated API client as the rest of the application.
it("loads export counts through the session-aware client", async () => {
  const options = { meetId: 7, meetName: "Demo Meet", options: [] };
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify(options)));
  expect(await getMeetExportOptions(7)).toEqual(options);
  expect(apiFetch).toHaveBeenCalledWith("/api/meets/7/exports", { signal: undefined });
});

// A spreadsheet download retains its server-provided name and explicit age reference.
it("downloads the CSV with the server filename and releases its object URL", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response("athlete,result\r\nDemo,12.4\r\n", { headers: { "Content-Disposition": 'attachment; filename="meet-7-results.csv"' } }));
  const create = vi.fn().mockReturnValue("blob:export-check"); const revoke = vi.fn();
  vi.stubGlobal("URL", { createObjectURL: create, revokeObjectURL: revoke });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  expect(await downloadMeetExport(7, "RESULTS", "2026-10-24")).toBe("meet-7-results.csv");
  expect(apiFetch).toHaveBeenCalledWith("/api/meets/7/export?type=RESULTS&ageAsOf=2026-10-24");
  expect(create).toHaveBeenCalledOnce();
  expect(await (create.mock.calls[0][0] as Blob).text()).toBe("athlete,result\r\nDemo,12.4\r\n");
  expect(click).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:export-check"); expect(document.querySelector("a[download]")).toBeNull();
});

// Empty-meet errors must reach the dialog rather than download a JSON error as CSV.
it("reports empty exports and does not trigger a browser download", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ message: "No recorded results are available for this meet" }), { status: 409 }));
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  await expect(downloadMeetExport(7, "RESULTS")).rejects.toThrow("No recorded results");
  expect(click).not.toHaveBeenCalled();
});

// Decode Unicode filenames and fall back safely when the header is absent.
it("accepts encoded filenames and a missing content disposition", () => {
  expect(fileNameFromDisposition("attachment; filename*=UTF-8''meet-caf%C3%A9.csv", "fallback.csv")).toBe("meet-caf\u00e9.csv");
  expect(fileNameFromDisposition(null, "fallback.csv")).toBe("fallback.csv");
});
