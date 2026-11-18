import { afterEach, describe, expect, it, vi } from "vitest";

import { getAthleteById, getAthletes, setAthletesArchived } from "./athleteService";
import { buildAthlete } from "../test/athleteTestData";

/**
 * Unit tests for the two service calls used by UC-01 and UC-02.
 * fetch is replaced with a fake, so no backend or database is needed.
 */

function fakeResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: /* Provide the json mock implementation. */ async () => body,
  } as Response;
}

// Replace fetch with a controllable mock for API-service tests.
function stubFetch(response: Response) {
  const fetchMock = vi.fn(/* Supply the mocked function behaviour used by this test. */ async () => response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(/* Clean up rendered content and mocks after each test. */ () => {
  vi.unstubAllGlobals();
});

describe("athleteService.getAthletes (UC-01)", /* Group the athleteService.getAthletes (UC-01) checks. */ () => {
  it("can include archived athletes for historical demographics", /* Verify that can include archived athletes for historical demographics. */ async () => {
    const fetchMock = stubFetch(fakeResponse([]));
    await getAthletes("all");
    expect(fetchMock).toHaveBeenCalledWith("/api/athletes?archive=all");
  });
  it("sends bulk archive and restore requests", /* Verify that sends bulk archive and restore requests. */ async () => {
    const fetchMock = stubFetch(fakeResponse([]));
    await setAthletesArchived([1, 2], true);
    expect(fetchMock).toHaveBeenCalledWith("/api/athletes/archive", expect.objectContaining({method:"PUT",body:JSON.stringify({athleteIds:[1,2],archived:true})}));
  });
  it("requests the directory endpoint and returns the athletes", /* Verify that requests the directory endpoint and returns the athletes. */ async () => {
    const athletes = [buildAthlete({ id: 1 }), buildAthlete({ id: 2 })];
    const fetchMock = stubFetch(fakeResponse(athletes));

    const result = await getAthletes();

    expect(fetchMock).toHaveBeenCalledWith("/api/athletes");
    expect(result).toEqual(athletes);
  });

  it("throws when the server responds with an error", /* Verify that throws when the server responds with an error. */ async () => {
    stubFetch(fakeResponse({ error: "boom" }, 500));

    await expect(getAthletes()).rejects.toThrow(
      "Unable to load athletes."
    );
  });
});

describe("athleteService.getAthleteById (UC-02)", /* Group the athleteService.getAthleteById (UC-02) checks. */ () => {
  it("requests the profile endpoint for the given ID", /* Verify that requests the profile endpoint for the given ID. */ async () => {
    const athlete = buildAthlete({ id: 7 });
    const fetchMock = stubFetch(fakeResponse(athlete));

    const result = await getAthleteById(7);

    expect(fetchMock).toHaveBeenCalledWith("/api/athletes/7");
    expect(result).toEqual(athlete);
  });

  it("throws when the athlete does not exist (404)", /* Verify that throws when the athlete does not exist (404). */ async () => {
    stubFetch(
      fakeResponse({ code: "ATHLETE_NOT_FOUND" }, 404)
    );

    await expect(getAthleteById(999)).rejects.toThrow(
      "Unable to load athlete."
    );
  });
});

// Isolate domain-service requests; CSRF and session transport have their own tests.
vi.mock("./apiClient", /* Provide isolated module replacements for this test file. */ () => ({ apiFetch: /* Provide the api fetch mock implementation. */ (...args: Parameters<typeof fetch>) => fetch(...args) }));
