# Sprint 2 frontend integration

Athletes remains the homepage (`/`), with profiles at `/athletes/:athleteId`.
The Meets List is at `/meets`, with details at `/meets/:meetId`.
The Results Overview is at `/results`.

The Athlete Directory follows the compact table layout: search and Add Athlete,
inline age/gender/status filters, the existing event multi-select, and Name,
Age Group, Gender, FLY Status, FLYA Status, and Actions columns. Status matches
either roster field. Missing values remain Not provided. Names open profiles;
the pencil enters the existing profile editor, and deletion requires confirmation.
The footer offers 10/25/50 rows per page. The profile layout and note separation
are retained. See the checks section below for the latest validation status.

## Athlete archive

The Directory provides Active athletes and Archived athletes views, row selection,
Select page, a persistent selection count across pages/filters, confirmation by
name, and bulk Archive/Restore. Switching views clears the selection. Profiles,
results, notes, and FLY/FLYA membership values are preserved; archive is a separate
database flag. Archived profiles show their status and remain accessible by ID.

`GET /api/athletes` defaults to active athletes. `?archive=archived` returns only
archived records; `?archive=all` includes both. The Directory uses all records to
show counts, and Results Overview includes both for historical demographics.
New event-assignment choices contain only active athletes. The backend rejects
new archived-athlete participation while retaining existing event history.

`PUT /api/athletes/archive` accepts `{ athleteIds: number[], archived: boolean }`,
validates the selection (up to 500 IDs), and archives/restores it transactionally.
An unknown ID rejects the whole batch. Responses contain the updated summaries.
Imported duplicates stay archived; importing does not silently restore records.
No live athlete was archived automatically as part of implementation.

The running backend limitations register is [BACKEND_LIMITATIONS.md](BACKEND_LIMITATIONS.md).
Keep its entries current as workarounds change or backend fixes become available.

## Import Athletes

The user took ownership of Import on 2026-11-02. `/import` is enabled in the main
navigation. `ImportAthletesPage` provides Upload, Preview & validate, Confirm,
and Summary. Its styles are scoped to `.import-*`; API calls live in
`athleteImportService.ts` and use the existing Vite `/api` proxy.

- `POST /api/athletes/import/preview`: multipart `file`; returns a SHA-256
  fingerprint and row-level values, seed marks, warnings, errors, and VALID,
  DUPLICATE, or ERROR status. It reads the database but never saves records.
- `POST /api/athletes/import/confirm`: multipart `file`, `fingerprint`, repeated
  `rows` integers; reparses the file and rechecks duplicates, then saves selected
  valid new athletes and seed marks in one transaction. Returns actual imported,
  duplicate, invalid, and unselected-valid counts with row outcomes. A changed
  file requires preview again. Existing athletes/results are not overwritten.
- The legacy immediate `/api/athletes/import` endpoint remains unchanged for
  compatibility. The new UI does not use it.

Files: XLSX (first worksheet), UTF-8 comma CSV, or TSV, up to 5 MB, 5,000 data
rows, and 128 columns. Legacy tab-separated `.csv` files are accepted. Headers
must identify the first seven columns in the original order: First Name,
Last Name, Note, FLY, FLYA, Date of Birth, Gender. Existing workbook header
spellings are supported. Additional nonblank columns identify seed disciplines.
Names are required. Missing DOB/gender produce warnings; nonblank invalid or
future DOBs produce errors. Dates use YYYY-MM-DD or Excel date cells. Excel
formulas must be replaced with values. Blank lines are ignored. CSV row numbers
identify parsed records; XLSX numbers identify worksheet rows.

Seed marks accept numeric seconds/metres, s/m/cm/min suffixes, or minutes:seconds.
They normalize to numeric seconds/metres with at most four decimal places and
the current score bounds. Known field events reject time units; known running
events reject distance units. DNS/DNF/DQ are not invented numeric marks. Imported
seeds remain unlinked historical records and do not create meet-event instances.
Duplicate matching normalizes name/gender plus DOB; without DOB it also compares
optional details and legacy seed marks. The first valid instance in a file wins.
The model's note column is widened to 500 characters to match existing API rules.

Downloadable sample: `public/samples/athlete-import-sample.csv`. On the current
roster it previews as 7 ready, 1 duplicate in the file, and 2 errors. The sample
has not been imported. Upload/preview does not change the current 125-athlete
roster. Ready rows are selected by default, review is paginated at 25 rows, and
duplicates/errors are disabled. Users can correct their file and upload again.

## Meets contract

The UI uses the existing `GET/POST /api/meets` and `PUT/DELETE /api/meets/{id}`.
Search and inclusive date-range filtering run locally on the fetched list.
The backend retains `meetDate` as the start date and adds optional `endDate`.
Blank end dates mean single-day meets. Responses normalize old null end dates to
the start date. Create/Edit validates the range; existing event dates must remain
inside it. Event creation/update validates the selected day against that range.
The list filter matches overlaps, and meet/history views display both dates for
multi-day meets. Add/Copy Events offers a day selector for the selected batch.
Meet status remains deferred. Optional location and website values use `null`;
the API rejects an empty string for `externalUrl`.

## Meet Details

Meet details use `GET /api/meets/{id}`; expandable event panels load participants
from `GET /api/events/{id}`. Event creation uses `POST /api/events`, with existing
types from `GET /api/event-categories`. New types can be created through the
existing event request's `categoryName` field. Copy Events creates event
definitions in the destination meet; it does not copy participants or results.
Dates and locations default to the destination meet.

### Shared participation and attempts

The earlier browser-only workarounds are retired. Assignments, every attempt,
event notes, and performance notes are persisted in MySQL. The participation
table follows the event_participations structure already introduced on the
team's development branch; this feature branch adds editable note support.

- Event profiles return participants separately from result attempts. Pending
  participants have no fabricated result. Participant counts include them.
- POST /api/events/{id}/participants/bulk saves selections transactionally.
- POST /api/events/{id}/results appends an attempt. A clientId makes retries
  idempotent. PUT /api/events/{id}/results/{resultId} edits that exact attempt.
- Leaderboards and the default Results view choose one best attempt per athlete;
  All attempts shows every stored attempt from any device.
- DELETE /api/events/{id}/participants/{athleteId} removes participation, its
  attempts, and its performance note together, preserving the athlete profile.

The idempotent MySQL startup migration runs after Hibernate creates new columns.
It removes only the old unique event_id/athlete_id result index, adds a supporting
non-unique lookup index, and backfills participation from existing linked results.
Existing results, IDs, display units, scores, and unlinked seed marks are retained.

Measurement type is inferred in `src/utils/eventResult.ts` from category/event
names and event type. The result form now provides a value and an explicit unit
choice. Timed events support seconds, decimal minutes, separate minutes/seconds,
and separate hours/minutes/seconds. Field events support metres and centimetres.
Sprints default to seconds; recognized races of at least 600m suggest separate
minutes/seconds; field events default to metres. Custom names fall back to
Field/DESC = distance, otherwise time.

Switching units converts an already valid value (90s becomes 1.5min; 5.2m becomes
520cm) instead of reinterpreting the same number. A live preview shows the saved
format and canonical conversion. Seconds entries can also accept pasted clock
times. Editing restores the entered unit/format, including legacy seconds,
metres and clock strings. Split-time blank fields count as zero; lower-order
minutes/seconds must be below 60. Invalid entries remain in the form with a
message and cannot silently change units.

The display string preserves the selected unit; the ranking value is normalized
to seconds/metres with up to four decimal places for DECIMAL(12,4). Structured
measurement/unit metadata remains a separate future enhancement.
Athletes with no result appear after recorded results; numeric results follow
the event's ranking order. Removing a participant also removes their stored attempts.
Event copying retains partial successes; bulk athlete assignment is atomic.

### Shared notes and earlier browser data

GET/PUT /api/events/{id}/note manages the event-wide note.
GET/PUT /api/events/{id}/participants/{athleteId}/note manages a performance note.
Both allow 2,000 characters; saving empty text clears the note. Load failures
block saving until the note is loaded, preventing accidental overwrites.
The general athlete note continues to use the existing athlete API.

Recovery controls are intentionally not mounted in the application UI, at the
user's request. The migration component and service remain available in source
for a deliberate recovery task if needed. Existing local records are untouched.
No current editing flow writes to browser storage; retired readers remain only
to support recovery.
The server migration operation is transactional per athlete/event, matches the
old shared best once, and gives attempts stable client IDs for safe retries.
Different shared notes are never overwritten automatically. Failed, malformed,
or orphaned browser records are retained for manual review. Source keys are
cleared only after successful migration. The sample meet remains read-only.

## Athlete Profile meet history

The Athlete Profile is organized into Overview, Meets & Results, and Notes tabs.
A compact summary keeps the athlete's name, ID, age and statuses visible above
the tabs. Overview has a personal-details grid and a collapsed Manage athlete
section; deletion still uses the existing confirmation flow.

Meets & Results shows expandable meets with aligned Event/Results/Notes columns,
which stack on narrow screens. Unmatched profile records remain in a collapsed
Other recorded results section. Confirmed result IDs already displayed in meet
history are excluded there to avoid showing the same result twice. No result
records are deleted by this presentation change.

Notes distinguishes an Athlete-wide note for general coaching context from
Meet & event specific performance notes, using matching cards, scope labels,
examples, and explicit actions. Storage remains the existing athlete API for the
general note and the event-participation API for performance notes. Edit Athlete starts in Overview; editing
the general note starts in Notes. Save/Cancel stay in the header and retain the
existing athlete request payload. Unsaved fields remain in state across tabs.
Save errors are visible above the tabs. Keyboard arrows/Home/End navigate tabs.

`AthleteMeetHistory` stays mounted while tabs switch, preserving fetched history,
expanded meets, and notes. Both note types and all performance history are now stored in the backend.

`athleteHistoryService.ts` joins the event and meet indexes, loads populated event
profiles using the same bounded loader as Results Overview, and filters those
profiles for the current athlete. It includes saved unscored participants, even when an event has no results. Meets are
grouped by meet ID and displayed most recent first, with expandable meets, event rows,
attempt chips, best-result badges, and links to Meet Details. If the meet index
fails, event dates are labeled explicitly instead of presented as meet dates.
Partial event failures are disclosed separately;
they do not prevent the rest of the athlete's profile from loading.

Performance notes can be edited beside a history event or in the Notes tab.
The shared performance-note API is also used in Meet Details.
The athlete's general backend Note field and its existing editor are separate.
Editing a performance note refreshes its preview without reloading every event.
History note-edit buttons are disabled while athlete information is being edited.

BL-12 documents the extra history requests; BL-17 documents the profile API's
central-versus-legacy result selection behavior. Neither required backend changes.

## Sample data

The backend returned no meets during implementation. Fictional sample data was
added only in `src/data/sampleMeet.ts`: one meet, three events, and example
athlete assignments/results. Open `/meets/demo` or use **Preview a sample meet**
from the empty Meets List. This is opt-in and read-only; it does not replace an
API response or seed the database. Negative IDs separate it from live records.
Sample notes are read-only. All API mutations
are disabled on the sample page.

## Results Overview

`src/services/resultsService.ts` uses the existing event index and loads populated
event profiles through `eventProfileService.ts` in batches of at most six. It joins the athlete directory for DOB
and gender. Filtering and pagination run locally; refreshing reloads the data.
Age groups are derived at the event date, using the existing directory groups.
Gender casing is normalized for consistent filtering. Missing values remain
Not provided. Results can be filtered by athlete name, event category, meet,
age group and gender.

The default **Shared best results** view shows the current shared performance per
athlete/event. **All attempts** shows all persisted attempt rows. Only performances associated with a meet are included;
unscored assignments and unlinked imported/historical results are excluded.

Individual event failures are listed rather than silently treated as empty data.
A failed demographic request still allows shared results to be viewed, with a
visible notice. Request cancellation stops further profile batches on navigation.

`/results?sample=1` reuses the existing sample-meet fixtures. No new demographic
values, database seeds, or automatic mock fallbacks were added for this page.
Sample results have Not provided for age group/gender and are clearly labeled.

Backend meet date-range changes were authorized on 2026-11-02. The existing
Hibernate schema-update setting adds end_date without dropping tables; the local
backend container is rebuilt to load the changes. Docker configuration is unchanged.

## Local fictional meet data (2026-11-02)

Added through the API, alongside the existing regional meet:

| Meet | Dates | Events | Results |
| --- | --- | --- | --- |
| Mock Autumn Open | Oct 10, 2026 | 4 | 16 |
| Mock Academy Weekend | Oct 17–18, 2026 | 4 | 16 |
| Mock Fall Championships | Oct 24–25, 2026 | 4 | 16 |

Each contains 100M, 200M, 800M, and LONG JUMP. Weekend meets schedule the first
two events on Saturday and the remaining two on Sunday. Events use ASC for track
and DESC for long jump. Sixteen fictional athletes recur across the three meets
to demonstrate profile history and result filtering. These are database-backed
records, distinct from `/meets/demo`. No browser-only attempts or performance
notes were prefilled. All 125 mock athletes and 161 unlinked seed results remain.
The original meet and its four event definitions were preserved. Verified totals:
4 meets, 16 events, 48 linked results, and no event dates outside meet ranges.

## Checks

The user resumed test work on 2026-11-02. Frontend tests cover directory search,
filters and pagination, bulk archive/restore/cancel/error paths, profiles and
notes, meet CRUD and weekend ranges, result filtering and unit conversion,
import preview/confirmation, navigation, and API services. External services are
mocked; frontend tests do not alter the application database.

Latest results after shared persistence: production build passed, ESLint passed,
and all 72 frontend tests passed across 15 files. Added coverage for shared
assignments, explicit attempt-ID editing, note load/save failures, participant
grouping, and preservation/retry of migrated browser notes. Manual API checks used
a temporary meet to confirm persistence after reload, independent attempts,
idempotent retries, shared notes, and participant removal; the meet was deleted
afterward. No backend test files were added or run.

Use Node 24 for the current frontend development dependencies. The system Node
20 produced engine warnings for installed test dependencies; checks use the
available Node 24 runtime. Run `npm run build`, `npm run lint`, and `npm test`
from `frontend`. No package versions or lockfile changes were needed to install
the already-declared development dependencies.

Test work is frontend-only, as clarified by the user. Newly added backend tests
were removed; the team's pre-existing backend tests remain untouched. No further
backend test runs are planned as part of this task.

Brief comments explain responsibilities and major logical blocks in frontend
pages, components, services, shared types, and utilities. Backend comments were
not expanded, as requested.
