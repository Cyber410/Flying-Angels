// Guides file upload, read-only preview, explicit confirmation, and import summary.
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Header from "../components/header";
import { confirmAthleteImport, previewAthleteImport } from "../services/athleteImportService";
import type { ImportPreview, ImportStatus, ImportSummary } from "../types/athleteImport";
import "./ImportAthletesPage.css";

type Step = "upload" | "preview" | "confirm" | "summary";
const steps: { id: Step; label: string }[] = [
  { id: "upload", label: "Upload file" }, { id: "preview", label: "Preview & validate" },
  { id: "confirm", label: "Confirm import" }, { id: "summary", label: "Summary" },
];
const statusLabels: Record<ImportStatus, string> = { VALID: "Ready", DUPLICATE: "Duplicate", ERROR: "Needs correction" };

export default function ImportAthletesPage() {
  const [step, setStep] = useState<Step>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState("");
  const [dragging, setDragging] = useState(false);
  const [filter, setFilter] = useState<ImportStatus | "ALL">("ALL");
  const [page, setPage] = useState(1);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const previewController = useRef<AbortController | null>(null);

  useEffect(() => { headingRef.current?.focus(); }, [step]);
  useEffect(() => () => previewController.current?.abort(), []);

  // Validates the selected file and clears stale preview information.
  function chooseFile(candidate?: File) {
    if (isBusy || !candidate) return;
    setError(""); setPreview(null); setSelected([]); setSummary(null); setPage(1); setFilter("ALL");
    if (!/\.(xlsx|csv|tsv)$/i.test(candidate.name)) {
      setFile(null); setError("Choose an XLSX, CSV, or TSV file."); return;
    }
    if (!candidate.size || candidate.size > 10 * 1024 * 1024) {
      setFile(null); setError("Choose a non-empty file up to 10 MB."); return;
    }
    setFile(candidate);
  }

  // Requests validation without writing athlete records.
  async function loadPreview() {
    if (!file || isBusy) return;
    setIsBusy(true); setError("");
    const controller = new AbortController();
    previewController.current = controller;
    try {
      const result = await previewAthleteImport(file, controller.signal);
      setPreview(result);
      setSelected(result.rows.filter(row => row.status === "VALID").map(row => row.rowNumber));
      setFilter("ALL"); setPage(1); setStep("preview");
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to preview this file.");
    } finally { setIsBusy(false); }
  }

  // Sends the reviewed rows for server revalidation and import.
  async function importSelected() {
    if (!file || !preview || !selected.length || isBusy) return;
    setIsBusy(true); setError("");
    try {
      const result = await confirmAthleteImport(preview.importId, selected);
      setSummary(result); setStep("summary");
    } catch (failure) { setError(failure instanceof Error ? failure.message : "The import could not be completed."); }
    finally { setIsBusy(false); }
  }

  // Starts a fresh upload after confirmation or a file change.
  function reset() {
    if (isBusy) return;
    setFile(null); setPreview(null); setSummary(null); setSelected([]); setError(""); setStep("upload");
    if (inputRef.current) inputRef.current.value = "";
  }

  // Selection spans preview pages; duplicate and invalid rows cannot be selected.
  const validRows = preview?.rows.filter(row => row.status === "VALID") ?? [];
  const filteredRows = preview?.rows.filter(row => filter === "ALL" || row.status === filter) ?? [];
  const totalPages = Math.max(1, Math.ceil(filteredRows.length / 25));
  const currentPage = Math.min(page, totalPages);
  const visibleRows = filteredRows.slice((currentPage - 1) * 25, currentPage * 25);
  const selectedMarks = preview?.rows.filter(row => selected.includes(row.rowNumber)).reduce((sum, row) => sum + row.seedMarks.length, 0) ?? 0;
  const excluded = (preview?.rows.length ?? 0) - selected.length;

  return <div className="import-page">
    <Header />
    <main className="import-container">
      <div className="import-heading"><h1>Import Athletes</h1><p>Review your file, choose the records to create or update, then confirm the import.</p></div>
      <ol className="import-steps" aria-label="Import progress">{steps.map((item, index) => <li key={item.id} className={item.id === step ? "active" : index < steps.findIndex(current => current.id === step) ? "complete" : ""} aria-current={item.id === step ? "step" : undefined}><span>{index + 1}</span>{item.label}</li>)}</ol>
      <section className="import-panel" aria-busy={isBusy}>
        <h2 ref={headingRef} tabIndex={-1}>{steps.find(item => item.id === step)?.label}</h2>
        {error && <div className="import-error" role="alert">{error}</div>}
        {/* Step 1: choose a file and explain the supported roster format. */}
        {step === "upload" && <>
          <div className={`import-dropzone${dragging ? " dragging" : ""}`} onDragOver={event => { event.preventDefault(); if (!isBusy) setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); if (event.dataTransfer.files.length !== 1) { setError("Choose one file at a time."); return; } chooseFile(event.dataTransfer.files[0]); }}>
            <p className="import-drop-title">Drop an athlete file here</p><p>or choose a file from your computer</p>
            <label className="import-file-picker">Choose file<input ref={inputRef} type="file" accept=".xlsx,.csv,.tsv" disabled={isBusy} onChange={event => chooseFile(event.target.files?.[0])} /></label>
            <p className="import-file-name">{file ? `${file.name} (${Math.ceil(file.size / 1024)} KB)` : "XLSX, CSV, or TSV · up to 10 MB"}</p>
          </div>
          <div className="import-format"><h3>Use the roster format</h3><p>The first seven columns must be First Name, Last Name, Note, FLY, FLYA, Date of Birth, and Gender. Optional seed-result columns follow. XLSX uses the first worksheet only.</p><p>Names are required. Use YYYY-MM-DD or Excel dates for birth dates. A valid birth date is required. Other details are optional. Seed times are seconds and distances are metres; seed marks stay separate from meet results.</p><a href="/samples/athlete-import-sample.csv" download>Download sample CSV</a><p className="import-muted">The sample contains valid athletes, one repeated row, and two rows to correct so you can explore validation.</p></div>
          <div className="import-actions"><button className="import-button primary" type="button" disabled={!file || isBusy} onClick={loadPreview}>{isBusy ? "Reading file…" : "Preview file"}</button></div>
        </>}
        {/* Step 2: review row-level issues and choose valid athletes across pages. */}
        {step === "preview" && preview && <>
          <p className="import-context">{file?.name} · {preview.rows.length} athlete rows. Nothing has been imported yet.</p>
          <div className="import-counts"><div><strong>{preview.validCount}</strong><span>Ready to import</span></div><div><strong>{preview.duplicateCount}</strong><span>Duplicates · skipped</span></div><div><strong>{preview.errorCount}</strong><span>Need correction · skipped</span></div></div>
          <div className="import-review-toolbar"><label>Show <select value={filter} onChange={event => {setFilter(event.target.value as ImportStatus | "ALL"); setPage(1);}}><option value="ALL">All rows</option><option value="VALID">Ready</option><option value="DUPLICATE">Duplicates</option><option value="ERROR">Need correction</option></select></label><span>{selected.length} selected</span><button className="import-text-button" type="button" disabled={!validRows.length} onClick={() => setSelected(selected.length === validRows.length ? [] : validRows.map(row => row.rowNumber))}>{selected.length === validRows.length && validRows.length ? "Deselect all" : "Select all ready rows"}</button></div>
          <div className="import-table-scroll" role="region" aria-label="Import preview" tabIndex={0}><table className="import-table"><thead><tr><th scope="col">Include</th><th scope="col">Row</th><th scope="col">Athlete</th><th scope="col">Date of birth</th><th scope="col">Status</th><th scope="col">Review</th></tr></thead><tbody>{visibleRows.map(row => <tr key={row.rowNumber}>
            <td><input type="checkbox" disabled={row.status !== "VALID"} checked={selected.includes(row.rowNumber)} aria-label={`Include row ${row.rowNumber}: ${row.firstName} ${row.lastName}`} onChange={event => setSelected(current => event.target.checked ? [...current, row.rowNumber] : current.filter(number => number !== row.rowNumber))} /></td>
            <td>{row.rowNumber}</td><th scope="row">{`${row.firstName} ${row.lastName}`.trim() || "Name missing"}</th><td>{row.dateOfBirth || "Not provided"}</td><td><span className={`import-status ${row.status.toLowerCase()}`}>{row.status === "VALID" && row.action === "UPDATE" ? "Update existing" : statusLabels[row.status]}</span></td>
            <td>{row.errors.map(message => <p className="import-row-error" key={message}>{message}</p>)}{row.warnings.map(message => <p className="import-row-warning" key={message}>{message}</p>)}<details><summary>{row.seedMarks.length} seed mark{row.seedMarks.length === 1 ? "" : "s"} · view details</summary><dl><div><dt>Gender</dt><dd>{row.gender || "Not provided"}</dd></div><div><dt>FLY / FLYA</dt><dd>{row.flyStatus || "Not provided"} / {row.flyaStatus || "Not provided"}</dd></div><div><dt>Note</dt><dd>{row.note || "Not provided"}</dd></div>{row.seedMarks.map(mark => <div key={mark.eventName}><dt>{mark.eventName}</dt><dd>{mark.result}</dd></div>)}</dl></details></td>
          </tr>)}</tbody></table>{!visibleRows.length && <p className="import-empty">No rows in this category.</p>}</div>
          <div className="import-pagination"><span>{filteredRows.length ? (currentPage - 1) * 25 + 1 : 0}–{Math.min(currentPage * 25, filteredRows.length)} of {filteredRows.length}</span><button className="import-button" type="button" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>Previous</button><span>Page {currentPage} of {totalPages}</span><button className="import-button" type="button" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>Next</button></div>
          {preview.errorCount > 0 && <p className="import-help">Correct rows in your source file, then upload it again. Valid rows can be imported now.</p>}
          {!preview.validCount && <p className="import-help">There are no eligible rows to import. Review the row explanations for details.</p>}
          <div className="import-actions"><button className="import-button" type="button" onClick={reset}>Choose another file</button><button className="import-button primary" type="button" disabled={!selected.length} onClick={() => {setError(""); setStep("confirm");}}>Review {selected.length} selected</button></div>
        </>}
        {/* Step 3: show the selection totals before the explicit database write. */}
        {step === "confirm" && preview && <>
          <p className="import-context">Ready to import the selected records from <strong>{file?.name}</strong>.</p>
          <dl className="import-confirm-counts"><div><dt>Athlete records selected</dt><dd>{selected.length}</dd></div><div><dt>Seed marks supplied</dt><dd>{selectedMarks}</dd></div><div><dt>Rows not selected</dt><dd>{excluded}</dd></div></dl>
          <p className="import-help">Rows marked Update existing will update the matched athlete using the supplied details. Blank cells keep existing values. The server rechecks each selected row before saving. Seed marks remain separate from meets.</p>
          {isBusy && <p role="status">Importing athletes. Keep this page open until the summary appears.</p>}
          <div className="import-actions"><button className="import-button" type="button" disabled={isBusy} onClick={() => {setError(""); setStep("preview");}}>Back to preview</button><button className="import-button primary" type="button" disabled={isBusy || !selected.length} onClick={importSelected}>{isBusy ? "Importing…" : `Import ${selected.length} athletes`}</button></div>
        </>}
        {/* Step 4: report actual saved/skipped counts returned by the backend. */}
        {step === "summary" && summary && <>
          <div className="import-summary-message" role="status"><h3>{summary.importedCount || summary.updatedCount ? "Import complete" : "No athlete records changed"}</h3><p>{summary.importedCount} athletes and {summary.seedResultCount} unlinked seed marks added.</p>{!!summary.updatedCount && <p>{summary.updatedCount} existing athlete records updated.</p>}</div>
          <dl className="import-confirm-counts"><div><dt>Duplicates skipped</dt><dd>{summary.duplicateCount}</dd></div><div><dt>Rows needing correction</dt><dd>{summary.invalidCount}</dd></div><div><dt>Ready rows not selected</dt><dd>{summary.notSelectedCount}</dd></div></dl>
          <p className="import-help">Only selected eligible rows were applied. Review skipped or unsuccessful rows below.</p>
          {summary.rows.some(row => row.status !== "IMPORTED" && row.status !== "UPDATED") && <details className="import-outcomes"><summary>Review skipped rows</summary><ul>{summary.rows.filter(row => row.status !== "IMPORTED" && row.status !== "UPDATED").map(row => <li key={row.rowNumber}>Row {row.rowNumber} · {row.name}: {row.message}</li>)}</ul></details>}
          <div className="import-actions"><button className="import-button" type="button" onClick={reset}>Import another file</button><Link className="import-button primary" to="/">View Athlete Directory</Link></div>
        </>}
      </section>
    </main>
  </div>;
}
