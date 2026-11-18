import { afterEach, describe, expect, it, vi } from "vitest";
import { confirmAthleteImport, previewAthleteImport } from "./athleteImportService";

const athlete = { firstName: "Fictional", lastName: "Runner", dateOfBirth: "2012-01-01", gender: "Female", flyStatus: "Active", flyaStatus: "", note: "", events: [{ eventName: "100M", result: "12.45s" }] };
afterEach(/* Clean up rendered content and mocks after each test. */ () => vi.unstubAllGlobals());

describe("Shared import API integration", /* Group the Shared import API integration checks. */ () => {
  it("maps new, update, unchanged, and invalid preview rows without committing", /* Verify that maps new, update, unchanged, and invalid preview rows without committing. */ async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ importId: "job-123", rows: [
      { rowNumber: 2, status: "NEW", athlete, explanation: "Create athlete" },
      { rowNumber: 3, status: "UPDATE", athlete, explanation: "Update team status" },
      { rowNumber: 4, status: "UNCHANGED", athlete, explanation: "Already exists" },
      { rowNumber: 5, status: "INVALID", athlete, explanation: "Birth date is required" },
    ] })));
    vi.stubGlobal("fetch", fetchMock);
    const file = new File(["sample"], "sample.csv");
    const preview = await previewAthleteImport(file);
    expect(preview).toMatchObject({ importId: "job-123", validCount: 2, duplicateCount: 1, errorCount: 1 });
    expect(preview.rows[1]).toMatchObject({ action: "UPDATE", status: "VALID", seedMarks: athlete.events });
    expect(preview.rows[3].errors).toEqual(["Birth date is required"]);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith("/api/athletes/import/preview", expect.objectContaining({ method: "POST", body: expect.any(FormData) }));
  });

  it("commits selected rows to their persisted preview and reports partial outcomes", /* Verify that commits selected rows to their persisted preview and reports partial outcomes. */ async () => {
    const statuses = ["IMPORTED", "UPDATED", "UNCHANGED", "FAILED", "SKIPPED"];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ importId: "job-123", imported: 1, updated: 1, skipped: 2, failed: 1,
      rows: statuses.map(/* Build fetch mock from each status. */ (status, index) => ({ rowNumber: index + 2, status, athlete, explanation: status })),
    })));
    vi.stubGlobal("fetch", fetchMock);
    const result = await confirmAthleteImport("job-123", [2, 3, 5]);
    expect(fetchMock).toHaveBeenCalledWith("/api/athletes/import/job-123/commit", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ rowNumbers: [2, 3, 5] }),
    });
    expect(result).toMatchObject({ importedCount: 1, updatedCount: 1, seedResultCount: 1, duplicateCount: 1, invalidCount: 1, notSelectedCount: 1 });
    expect(result.rows.map(/* Convert each row into the required display or request data. */ row => row.status)).toEqual(["IMPORTED", "UPDATED", "DUPLICATE", "ERROR", "NOT_SELECTED"]);
  });

  it("reports a missing preview without issuing a replacement import", /* Verify that reports a missing preview without issuing a replacement import. */ async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "Import not found" }), { status: 404 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(confirmAthleteImport("missing", [2])).rejects.toThrow("Import not found");
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

// Isolate domain-service requests; CSRF and session transport have their own tests.
vi.mock("./apiClient", /* Provide isolated module replacements for this test file. */ () => ({ apiFetch: /* Provide the api fetch mock implementation. */ (...args: Parameters<typeof fetch>) => fetch(...args) }));
