// Meet Details CSV exports served by MeetExportController.
import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";

export type MeetExportType = "REGISTRATION" | "RESULTS";

export type MeetExportOption = {
  type: MeetExportType;
  displayName: string;
  recordCount: number;
  available: boolean;
};

export type MeetExportOptions = {
  meetId: number;
  meetName: string;
  options: MeetExportOption[];
};

// GET /api/meets/{id}/exports: export types with their record counts.
export async function getMeetExportOptions(meetId: number, signal?: AbortSignal): Promise<MeetExportOptions> {
  const response = await apiFetch(`/api/meets/${meetId}/exports`, { signal });
  await checkResponse(response, "Unable to load export options.");
  return response.json();
}

// Reads the server's file name from Content-Disposition, e.g.
// attachment; filename="meet-3-fall-invitational-registration.csv"
export function fileNameFromDisposition(header: string | null, fallback: string): string {
  const match = header?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  if (!match) return fallback;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

// GET /api/meets/{id}/export?type=...: the CSV file. An empty export is
// rejected by the server with a message such as
// "No registration records are available for this meet".
export async function downloadMeetExport(meetId: number, type: MeetExportType, ageAsOf?: string): Promise<string> {
  const params = new URLSearchParams({ type });
  if (ageAsOf) params.set("ageAsOf", ageAsOf);
  const response = await apiFetch(`/api/meets/${meetId}/export?${params}`);
  await checkResponse(response, "The export could not be created. Please try again.");

  const fileName = fileNameFromDisposition(
    response.headers.get("Content-Disposition"),
    `meet-${meetId}-${type.toLowerCase()}.csv`,
  );
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  return fileName;
}
