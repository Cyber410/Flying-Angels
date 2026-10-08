import { afterEach, describe, expect, it, vi } from "vitest";

import { getAthleteById, getAthletes } from "./athleteService";
import { buildAthlete } from "../test/athleteTestData";

/**
 * Unit tests for the two service calls used by UC-01 and UC-02.
 * fetch is replaced with a fake, so no backend or database is needed.
 */

function fakeResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

function stubFetch(response: Response) {
  const fetchMock = vi.fn(async () => response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("athleteService.getAthletes (UC-01)", () => {
  it("requests the directory endpoint and returns the athletes", async () => {
    const athletes = [buildAthlete({ id: 1 }), buildAthlete({ id: 2 })];
    const fetchMock = stubFetch(fakeResponse(athletes));

    const result = await getAthletes();

    expect(fetchMock).toHaveBeenCalledWith("/api/athletes");
    expect(result).toEqual(athletes);
  });

  it("throws when the server responds with an error", async () => {
    stubFetch(fakeResponse({ error: "boom" }, 500));

    await expect(getAthletes()).rejects.toThrow(
      "Unable to load athletes."
    );
  });
});

describe("athleteService.getAthleteById (UC-02)", () => {
  it("requests the profile endpoint for the given ID", async () => {
    const athlete = buildAthlete({ id: 7 });
    const fetchMock = stubFetch(fakeResponse(athlete));

    const result = await getAthleteById(7);

    expect(fetchMock).toHaveBeenCalledWith("/api/athletes/7");
    expect(result).toEqual(athlete);
  });

  it("throws when the athlete does not exist (404)", async () => {
    stubFetch(
      fakeResponse({ code: "ATHLETE_NOT_FOUND" }, 404)
    );

    await expect(getAthleteById(999)).rejects.toThrow(
      "Unable to load athlete."
    );
  });
});
