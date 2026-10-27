import { afterEach, describe, expect, it, vi } from "vitest";
import { createMeet, deleteMeet, getMeets, updateMeet } from "./meetService";
import type { Meet, MeetRequest } from "../types/meet";

const request: MeetRequest = {
  name: "Fall Invitational",
  meetDate: "2026-10-24",
  location: "Toronto, ON",
  externalUrl: null,
};
const meet: Meet = { ...request, id: 7, eventCount: 3 };

function stubResponse(body: unknown, status = 200) {
  const response = {
    ok: status >= 200 && status < 300,
    json: vi.fn().mockResolvedValue(body),
  };
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, response };
}

afterEach(() => vi.unstubAllGlobals());

describe("meetService existing backend contract", () => {
  it("loads the unpaginated meet list", async () => {
    const { fetchMock } = stubResponse([meet]);
    await expect(getMeets()).resolves.toEqual([meet]);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets");
  });

  it("creates a meet using the DTO field names and nullable optional URL", async () => {
    const { fetchMock } = stubResponse(meet, 201);
    await expect(createMeet(request)).resolves.toEqual(meet);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  });

  it("updates the selected meet by ID", async () => {
    const { fetchMock } = stubResponse(meet);
    await expect(updateMeet(7, request)).resolves.toEqual(meet);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets/7", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  });

  it("handles a successful 204 deletion without parsing JSON", async () => {
    const { fetchMock, response } = stubResponse(undefined, 204);
    await expect(deleteMeet(7)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/meets/7", { method: "DELETE" });
    expect(response.json).not.toHaveBeenCalled();
  });

  it("surfaces the backend validation message", async () => {
    stubResponse({ code: "VALIDATION_ERROR", message: "name: Meet name is required" }, 400);
    await expect(createMeet(request)).rejects.toThrow("name: Meet name is required");
  });

  it("uses a readable fallback for a non-JSON proxy error", async () => {
    const { response } = stubResponse(null, 502);
    response.json.mockRejectedValue(new SyntaxError("Unexpected token"));
    await expect(getMeets()).rejects.toThrow("Unable to load meets.");
  });

  it.each([
    ["update", () => updateMeet(7, request), "Unable to update meet."],
    ["delete", () => deleteMeet(7), "Unable to delete meet."],
  ])("rejects failed %s operations", async (_operation, action, message) => {
    stubResponse({}, 500);
    await expect(action()).rejects.toThrow(message);
  });

  it("rejects network failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(getMeets()).rejects.toThrow("Failed to fetch");
  });
});
