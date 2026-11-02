// Preview rows and final import outcomes returned by the backend.
export type ImportStatus = "VALID" | "DUPLICATE" | "ERROR";
export type ImportRow = {
  rowNumber: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  flyStatus: string;
  flyaStatus: string;
  note: string;
  seedMarks: { eventName: string; result: string }[];
  status: ImportStatus;
  errors: string[];
  warnings: string[];
};
export type ImportPreview = {
  filename: string;
  fingerprint: string;
  validCount: number;
  duplicateCount: number;
  errorCount: number;
  rows: ImportRow[];
};
export type ImportSummary = {
  importedCount: number;
  seedResultCount: number;
  duplicateCount: number;
  invalidCount: number;
  notSelectedCount: number;
  rows: { rowNumber: number; name: string; status: "IMPORTED" | "DUPLICATE" | "ERROR" | "NOT_SELECTED"; message: string }[];
};
