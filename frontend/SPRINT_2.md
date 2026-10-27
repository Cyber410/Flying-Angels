# Sprint 2 frontend integration

Athletes remains the homepage (`/`), with profiles at `/athletes/:athleteId`.
The Meets List is at `/meets`, with details at `/meets/:meetId`.
The Results Overview is at `/results`.

The running backend limitations register is [BACKEND_LIMITATIONS.md](BACKEND_LIMITATIONS.md).
Keep its entries current as workarounds change or backend fixes become available.

## Import Athletes handoff

`/import` is reserved. No Import page, service, or workflow has been implemented.
To integrate the separately developed Import section:

1. Add its page and a `/import` route in `src/App.tsx`.
2. Replace the disabled Import span in `src/components/header.tsx` with
   `<NavLink to="/import" className="site-nav-link">Import</NavLink>`.
3. Include the existing `Header` in the page, following the athlete and meet pages.
   The router is already provided by `App`; do not add another `BrowserRouter`.
4. Use the Inter font and shared CSS variables in `src/index.css`. Scope page
   styles to Import-specific classes so they do not affect other pages.
5. Keep API calls in a separate service module. Relative `/api` requests use the
   existing Vite proxy to the Docker backend on port 8080.

## Meets contract

The UI uses the existing `GET/POST /api/meets` and `PUT/DELETE /api/meets/{id}`.
Search and inclusive date-range filtering run locally on the fetched list.
The backend has one `meetDate` and no meet status or separate end date, so those
wireframe fields are deferred. Optional location and website values use `null`;
the API rejects an empty string for `externalUrl`.

## Meet Details

Meet details use `GET /api/meets/{id}`; expandable event panels load participants
from `GET /api/events/{id}`. Event creation uses `POST /api/events`, with existing
types from `GET /api/event-categories`. New types can be created through the
existing event request's `categoryName` field. Copy Events creates event
definitions in the destination meet; it does not copy participants or results.
Dates and locations default to the destination meet.

### Assignment workaround (frontend only)

`POST /api/events/{id}/participants` is currently broken: the service constructs
an EventResult with a null result, while its constructor calls `result.trim()`.
The user declined a backend change. The frontend therefore does not call this
endpoint. No dummy result or score is sent to bypass it.

Unscored assignments use `src/services/eventAssignmentStore.ts` to save athlete
IDs/names in localStorage under
`flying-angels:pending-event-assignments:{eventId}`. They survive reloads on the
same browser/origin and are clearly marked **Browser only**. They are not shared
with teammates and clearing browser data removes them. Storage failures are
reported rather than treated as successful saves.

Recording the first real result uses `POST /api/events/{id}/results`, which
creates the shared assignment/result together, then clears the browser entry.
Later result edits use `PUT /api/events/{id}/participants/{athleteId}/result`.
If a shared record already exists, it takes precedence over browser-only data.
Browser-only removals update localStorage; shared removals use
`DELETE /api/events/{id}/results/{resultId}`. Both preserve the athlete profile.

The API supports one shared result per athlete per event. The frontend supports
multiple attempts through `src/services/eventAttemptStore.ts`, stored in this
browser under `flying-angels:event-attempts:{eventId}:{athleteId}`. Each attempt is
shown as a separate editable chip. **+ Result** always adds an attempt; clicking
a chip edits that attempt. Existing shared results are retained in the history.

The best attempt is sent to the existing API: the lowest score for ASC events,
or the highest for DESC events. All other attempts are browser-only and not
visible to teammates; clearing browser data removes their history. The UI labels
this limit. A temporary `:staged` entry protects committed history while syncing
the best result. Failed API calls leave the previous committed history intact.
Successful syncs retain a recoverable committed staging entry if storage
compaction fails.

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
to seconds/metres and rounded to four decimal places for DECIMAL(12,4). No API
or database change was made. BL-06 and BL-16 track the remaining metadata and
precision limitations in the running register.
Athletes with no result appear after recorded results; numeric results follow
the event's ranking order. Removing an athlete also clears their browser attempts.
Batch creation and assignment retain successful items if other items fail;
failed items remain selected for retry.

### Browser-only notes

There is no event-notes endpoint. With the user's approval, event notes and
athlete-specific event notes use localStorage through
`src/components/EventNotesDialog.tsx`. They are labeled as browser-only in the UI.
They are not shared, synced, or written to the backend. Keys are:
`flying-angels:event-note:live:{eventId}:{athleteId-or-event}`.
Saving a blank note removes it. No note contents are seeded.

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

Notes separates the general athlete note (shared through the existing athlete
API) from browser-only performance notes. Edit Athlete starts in Overview; editing
the general note starts in Notes. Save/Cancel stay in the header and retain the
existing athlete request payload. Unsaved fields remain in state across tabs.
Save errors are visible above the tabs. Keyboard arrows/Home/End navigate tabs.

`AthleteMeetHistory` stays mounted while tabs switch, preserving fetched history,
expanded meets, and notes. A single expandable notice explains shared versus
browser-only data; repeated storage explanations and duplicate dates are reduced.

`athleteHistoryService.ts` joins the event and meet indexes, loads populated event
profiles using the same bounded loader as Results Overview, and filters those
profiles for the current athlete. It also includes unscored assignments stored
in this browser, even when an event has no shared participants. Meets are
grouped by meet ID and displayed most recent first, with expandable meets, event rows,
attempt chips, best-result badges, and links to Meet Details. If the meet index
fails, event dates are labeled explicitly instead of presented as meet dates.
Partial event failures and unreadable browser stores are disclosed separately;
they do not prevent the rest of the athlete's profile from loading.

Performance notes can be edited beside a history event or in the Notes tab.
`eventNotesStore.ts` preserves the existing storage
keys, so these are the same athlete-specific event notes used in Meet Details.
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
Sample notes use a separate `sample` localStorage namespace. All API mutations
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
athlete/event. **All attempts in this browser** adds the local history, with
browser-only rows labeled. Only performances associated with a meet are included;
unscored assignments and unlinked imported/historical results are excluded.

Individual event failures are listed rather than silently treated as empty data.
A failed demographic request still allows shared results to be viewed, with a
visible notice. Request cancellation stops further profile batches on navigation.

`/results?sample=1` reuses the existing sample-meet fixtures. No new demographic
values, database seeds, or automatic mock fallbacks were added for this page.
Sample results have Not provided for age group/gender and are clearly labeled.

No backend or Docker configuration changes are part of these increments.

## Checks

Build, lint, and tests are deferred at the user's request. When ready, run
`npm run build`, `npm run lint`, and `npm test` from `frontend`.
