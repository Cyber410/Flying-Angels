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

// Provide the mocked HTTP response used by the meet-service test.
function stubResponse(body: unknown, status = 200) {
  const response = {
    ok: status >= 200 && status < 300,
    json: vi.fn().mockResolvedValue(body),
  };
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, response };
}

afterEach(/* Clean up rendered content and mocks after each test. */ () => vi.unstubAllGlobals());

describe("meetService existing backend contract", /* Group the meetService existing backend contract checks. */ () => {
  it("loads the unpaginated meet list", /* Verify that loads the unpaginated meet list. */ async () => {
    const { fetchMock } = stubResponse([meet]);
    await expect(getMeets()).resolves.toEqual([meet]);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets");
  });

  it("uses the backend schedule filter when requested", /* Verify that schedule classification is performed by the API. */ async () => {
    const { fetchMock } = stubResponse([meet]);
    await expect(getMeets(undefined, "PAST")).resolves.toEqual([meet]);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets?view=PAST");
  });

  it("creates a meet using the DTO field names and nullable optional URL", /* Verify that creates a meet using the DTO field names and nullable optional URL. */ async () => {
    const { fetchMock } = stubResponse(meet, 201);
    await expect(createMeet(request)).resolves.toEqual(meet);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  });

  it("updates the selected meet by ID", /* Verify that updates the selected meet by ID. */ async () => {
    const { fetchMock } = stubResponse(meet);
    await expect(updateMeet(7, request)).resolves.toEqual(meet);
    expect(fetchMock).toHaveBeenCalledWith("/api/meets/7", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  });

  it("handles a successful 204 deletion without parsing JSON", /* Verify that handles a successful 204 deletion without parsing JSON. */ async () => {
    const { fetchMock, response } = stubResponse(undefined, 204);
    await expect(deleteMeet(7)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/meets/7", { method: "DELETE" });
    expect(response.json).not.toHaveBeenCalled();
  });

  it("surfaces the backend validation message", /* Verify that surfaces the backend validation message. */ async () => {
    stubResponse({ code: "VALIDATION_ERROR", message: "name: Meet name is required" }, 400);
    await expect(createMeet(request)).rejects.toThrow("name: Meet name is required");
  });

  it("uses a readable fallback for a non-JSON proxy error", /* Verify that uses a readable fallback for a non-JSON proxy error. */ async () => {
    const { response } = stubResponse(null, 502);
    response.json.mockRejectedValue(new SyntaxError("Unexpected token"));
    await expect(getMeets()).rejects.toThrow("Unable to load meets.");
  });

  it.each([
    ["update", /* Invoke the update request for this parameterized test case. */ () => updateMeet(7, request), "Unable to update meet."],
    ["delete", /* Invoke the delete request for this parameterized test case. */ () => deleteMeet(7), "Unable to delete meet."],
  ])("rejects failed %s operations", /* Verify the same behaviour for each supplied test case. */ async (_operation, action, message) => {
    stubResponse({}, 500);
    await expect(action()).rejects.toThrow(message);
  });

  it("rejects network failures", /* Verify that rejects network failures. */ async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(getMeets()).rejects.toThrow("Failed to fetch");
  });
});

// Isolate domain-service requests; CSRF and session transport have their own tests.
vi.mock("./apiClient", /* Provide isolated module replacements for this test file. */ () => ({ apiFetch: /* Provide the api fetch mock implementation. */ (...args: Parameters<typeof fetch>) => fetch(...args) }));
