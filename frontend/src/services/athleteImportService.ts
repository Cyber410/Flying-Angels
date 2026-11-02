// Uploads files for preview and confirms only the selected row numbers.
import type { ImportPreview, ImportSummary } from "../types/athleteImport";
import { checkResponse } from "./apiResponse";

export async function previewAthleteImport(file: File, signal?: AbortSignal): Promise<ImportPreview> {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch("/api/athletes/import/preview", { method: "POST", body, signal });
  await checkResponse(response, "The file could not be previewed. Check its format and try again.");
  return response.json();
}

export async function confirmAthleteImport(file: File, fingerprint: string, rows: number[]): Promise<ImportSummary> {
  const body = new FormData();
  body.append("file", file);
  body.append("fingerprint", fingerprint);
  rows.forEach(row => body.append("rows", String(row)));
  const response = await fetch("/api/athletes/import/confirm", { method: "POST", body });
  await checkResponse(response, "The import could not be confirmed. Preview the file again before retrying; any athletes already saved will be detected as duplicates.");
  return response.json();
}
