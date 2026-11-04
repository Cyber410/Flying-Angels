// Adapts the shared import module to the upload, review, and summary screens.
import type { ImportPreview, ImportRow, ImportSummary } from "../types/athleteImport";
import { checkResponse } from "./apiResponse";

type ApiRow = {
  rowNumber: number;
  status: string;
  explanation: string | null;
  athlete: {
    firstName: string; lastName: string; dateOfBirth: string | null; gender: string | null;
    flyStatus: string | null; flyaStatus: string | null; note: string | null;
    events: { eventName: string; result: string }[] | null;
  };
};
type ApiPreview = { importId: string; rows: ApiRow[] };
type ApiSummary = ApiPreview & { imported: number; updated: number; skipped: number; failed: number };

function previewRow(row: ApiRow): ImportRow {
  const eligible = ["NEW", "UPDATE", "VALID"].includes(row.status);
  const duplicate = ["UNCHANGED", "DUPLICATE"].includes(row.status);
  const athlete = row.athlete;
  return {
    rowNumber: row.rowNumber, firstName: athlete.firstName, lastName: athlete.lastName,
    dateOfBirth: athlete.dateOfBirth ?? "", gender: athlete.gender ?? "",
    flyStatus: athlete.flyStatus ?? "", flyaStatus: athlete.flyaStatus ?? "", note: athlete.note ?? "",
    seedMarks: athlete.events ?? [], status: eligible ? "VALID" : duplicate ? "DUPLICATE" : "ERROR",
    action: row.status === "UPDATE" ? "UPDATE" : "NEW",
    errors: !eligible && !duplicate ? [row.explanation ?? "Review this row before importing."] : [],
    warnings: eligible || duplicate ? [row.explanation ?? (duplicate ? "Already in the directory." : "Ready to import.")] : [],
  };
}

export async function previewAthleteImport(file: File, signal?: AbortSignal): Promise<ImportPreview> {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch("/api/athletes/import/preview", { method: "POST", body, signal });
  await checkResponse(response, "The file could not be previewed. Check its format and try again.");
  const data: ApiPreview = await response.json();
  const rows = data.rows.map(previewRow);
  return {
    filename: file.name, importId: data.importId, rows,
    validCount: rows.filter(row => row.status === "VALID").length,
    duplicateCount: rows.filter(row => row.status === "DUPLICATE").length,
    errorCount: rows.filter(row => row.status === "ERROR").length,
  };
}

// Confirmation references the persisted preview and submits only reviewed row numbers.
export async function confirmAthleteImport(importId: string, rows: number[]): Promise<ImportSummary> {
  const response = await fetch(`/api/athletes/import/${encodeURIComponent(importId)}/commit`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ rowNumbers: rows }),
  });
  await checkResponse(response, "The import could not be confirmed. Review the preview before retrying.");
  const data: ApiSummary = await response.json();
  return {
    importedCount: data.imported, updatedCount: data.updated,
    seedResultCount: data.rows.filter(row => row.status === "IMPORTED").reduce((sum, row) => sum + (row.athlete.events?.length ?? 0), 0),
    duplicateCount: data.rows.filter(row => ["UNCHANGED", "DUPLICATE"].includes(row.status)).length,
    invalidCount: data.rows.filter(row => ["FAILED", "INVALID", "AMBIGUOUS"].includes(row.status)).length,
    notSelectedCount: data.rows.filter(row => row.status === "SKIPPED").length,
    rows: data.rows.map(row => ({
      rowNumber: row.rowNumber, name: `${row.athlete.firstName} ${row.athlete.lastName}`.trim(),
      status: row.status === "IMPORTED" ? "IMPORTED" : row.status === "UPDATED" ? "UPDATED" : row.status === "SKIPPED" ? "NOT_SELECTED" : ["UNCHANGED", "DUPLICATE"].includes(row.status) ? "DUPLICATE" : "ERROR",
      message: row.explanation ?? row.status,
    })),
  };
}
