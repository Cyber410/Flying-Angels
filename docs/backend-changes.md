# Backend changes and rationale

For the complete project documentation, including structure, design, API reference, setup, and operations, read [backend/README.md](../backend/README.md).

This update implements the backend for meet management, reusable event definitions, athlete participation, multiple results, note history, and athlete-import preview/selection. Existing athlete routes remain available. Diagrams now describe the implementation and have no requirement-gap footers.

## What changed and why

| Change | Why it was needed | Resulting behavior |
| --- | --- | --- |
| Separate `EventParticipation` from `EventResult` | Assignments previously required a null result, which crashed the constructor; result uniqueness also prevented multiple attempts | One assignment per event/athlete; independently identified result records can be added repeatedly |
| Meet start/end dates | A single date could not represent a multi-day meet | Validated date ranges; event dates must stay within their meet; existing `meetDate` is supported as a start-date alias |
| Meet/category uniqueness and bulk/copy services | Duplicate events were possible and copying was not implemented | One occurrence of each category per meet; bulk operations are atomic; selected copying creates new events without assignments/results |
| Result measurement normalization | String values could not consistently support comparison | Definitions have units; track times normalize to seconds, field measurements to metres, and point events to points; non-performance statuses have null scores |
| Append participant performances | Recording a new performance overwrote the previous record | POST result and the legacy participant-result route append attempts; PUT by result ID corrects one record |
| Consistent athlete profiles | PUT responses omitted central results, and central results hid unmatched historical values | Service assembles GET/PUT profiles with results, meet/event context, and participation; unmatched legacy values remain visible |
| Dedicated timestamped notes | One profile string could not retain note history | New notes append independently and return newest first; the existing general note remains intact |
| One import module and persisted previews | Two overlapping import services applied different duplicate policies | All import endpoints use importing/AthleteImportService, one parser/matcher and per-row writer; no import services remain in athlete/ |
| Import creates or updates | Existing athletes were skipped even when registration information changed | Valid DOB is required; supplied nonblank changes update the matched athlete; only unchanged rows are skipped; history is preserved |
| Explicit validation/duplicate explanations | Missing, malformed and duplicated spreadsheet data was difficult to diagnose | Preview differentiates NEW, UPDATE, UNCHANGED, INVALID and AMBIGUOUS rows; commit returns imported/updated/skipped/failed counts |
| Source-scoped external identifiers | A name-based hash is not a reliable registration identifier | Optional MEMBER # / registration ID is unique within the supplied source; ambiguous identity matches are excluded for review |
| Hibernate-managed current schema | Initial data comes from the client CSV into a fresh database | Hibernate creates/updates tables from entity mappings; no migration files or runner are required |
| Production access policy | Every endpoint previously allowed anonymous modification | `prod` profile requires authentication; reads permit viewer/coach/admin, writes coach/admin, and deletes/imports admin; write requests require CSRF tokens |

`EventCategory` continues to mean a reusable event definition. `Event` continues to mean an event within a meet. Keeping these names avoids a broad rename of existing routes and files.

## API additions and behavior

### Meets and events

```json
POST /api/meets
{
  "name": "Spring Meet",
  "startDate": "2026-05-16",
  "endDate": "2026-05-18",
  "location": "Stadium"
}
```

The original `meetDate` request still creates a single-day meet. If both `meetDate` and `startDate` are provided they must agree. Omitting `endDate` makes the meet single-day.

- `GET /api/meets/{id}/events`: all events for a meet.
- `POST /api/meets/{id}/events/bulk`: `{ "events": [ /* existing EventRequest objects */ ] }`; every object must reference the target meet. A failure rolls back the batch.
- `POST /api/meets/{id}/events/copy`: `{ "sourceMeetId": 1, "eventIds": [2, 3] }`. Only selected events belonging to that source meet are copied. Copies use the target meet's start date/location and preserve the source definition/event metadata, with no athletes or results.
- Existing event CRUD remains available. An event with results cannot move to another meet or category.
- Event-definition creation accepts optional `unit`: `SECONDS`, `METRES`, or `POINTS`. Existing requests derive a default from the definition name/ranking direction.

The chosen duplicate rule is one category per meet. If heats, rounds, relays or age divisions need separate instances of the same category, extend this identity rule before adding that workflow.

### Participation and results

- `POST /api/events/{id}/participants`: `{ "athleteId": 7 }`; response is an `EventParticipationResponse`, rather than an empty result.
- `GET /api/events/{id}/participants`: athlete list with event/meet context.
- `POST /api/events/{id}/participants/bulk`: `{ "athleteIds": [7, 8] }`; atomic, rejecting repeated/already assigned IDs.
- `DELETE /api/events/{id}/participants/{athleteId}`: removes the assignment and its result attempts. Deleting an individual result leaves participation intact.
- `GET /api/events/{id}/results`: all attempts.
- `POST /api/events/{id}/results`: `{ "athleteId": 7, "result": "2:44.07", "unit": "SECONDS" }`. Score is derived (`164.07`); when supplied, it must match. A missing assignment is created.
- `POST /api/events/{id}/results/bulk`: `{ "results": [ /* result requests */ ] }`; atomic, supports multiple attempts for the same athlete.
- Existing result-ID PUT and DELETE remain correction/deletion operations.
- Existing `PUT /api/events/{id}/participants/{athleteId}/result` now appends a performance. It is a compatibility route, **not an idempotent replacement**; new clients should use POST results.
- Leaderboard returns each athlete's best numeric attempt within that event. Name ordering remains ascending for ties. It is not a season ranking or an eligibility-verified personal-best calculation.

Result responses retain the original fields and add `eventId`, `eventName`, `meetId`, `meetName`, `unit`, `status`, `recordedAt`, and `source`. Historical records may have null IDs/context/score/time. Frontend result types still need to reflect that nullability; frontend changes are outside this backend work.

### Notes and athlete profiles

- `POST /api/athletes/{id}/notes`: `{ "text": "Observation", "eventId": 12 }`; `eventId` is optional.
- `GET /api/athletes/{id}/notes`: timestamped notes, newest first, with event/meet information when available.
- Removing an event/meet preserves note text and detaches its event link. Removing an athlete removes its dependent notes, results and assignments.
- Athlete GET/PUT profiles now also contain `participations[]`.
- Historical values matching an existing central category/value are suppressed in the display to avoid showing both storage copies; unmatched legacy values remain visible. Storage is preserved rather than automatically merging unknown performances.
- Omitting `note` or legacy `events` from an update preserves them. Supplying legacy `events` explicitly replaces that legacy collection; it does not modify central result history. Dedicated result endpoints are the preferred editing path.
- Known spellings normalize to `In Roster`, `Not In Roster`, `Not On Team`, and `Added`. Missing statuses return an empty string for compatibility. Unknown values are retained; club-approved membership-state semantics are still needed before replacing these with a closed enum.

## Importing the supplied club spreadsheet

The supplied headers map first/last names, NOTE, both long FLY/FLYA headings, DATE OF BIRTH, Gender, and all 31 event columns. Column order can change. Blank rows and blank spacer headings are supported; administrative columns stay in the original file rather than becoming performances.

```text
POST /api/athletes/import/preview
multipart fields: file, source (optional; default UPLOAD)

GET /api/athletes/import/{importId}

POST /api/athletes/import/{importId}/commit
{ "rowNumbers": [2, 4, 7] }
```

NEW rows create athletes and UPDATE rows apply supplied changes to confirmed existing profiles. UNCHANGED confirmed rows are skipped without modifying data. Every imported row requires a valid past DOB; missing or invalid DOB makes the row INVALID, even for a matched athlete with a stored DOB. Registrations with confirmed identifiers can fill or correct legacy profile DOB/gender/status/note information. Blank optional cells do not erase stored values. AMBIGUOUS includes an existing name/DOB match without a supplied identifier, even one match. See [ADR-0008](adr/0008-required-import-birth-dates.md) and [ADR-0009](adr/0009-name-dob-and-confirmed-identifiers.md).

Matching first uses a source-scoped external identifier. Without an identifier match, known different DOBs identify separate people even when names match. An existing exact name/DOB match requires a supplied identifier before updating or classifying unchanged; gender does not remove this requirement. A supplied identifier can attach to one unkeyed exact name/DOB match. Repeated confirmed identifiers can enrich earlier rows and append performances; repeated unkeyed name/DOB rows require review after the first creation.

Both upload routes use the same `importing/AthleteImportService`, parser, matcher and row writer. Athlete CRUD has no separate import implementation. Each row's create/update and historical additions are transactional; a failed row rolls back while successful rows remain saved. Retrying an IMPORTED or UPDATED preview row does not apply it again. Commit counts are cumulative per job and include a separate updated count.

### Handling the actual sample values

| Source value | Handling |
| --- | --- |
| Blank DOB | INVALID; row cannot be imported |
| Blank gender | Allowed, with a preview warning |
| `2010-03-21` | Valid birth date; used to create/fill/correct the profile |
| Missing DOB, `40700`, `(Not found)`, `No birth date`, `Roshaine`, today/future dates in DOB | INVALID; row is not applied and existing stored data remains untouched |
| Properly typed XLSX date cell | Converted to ISO date using workbook date handling |
| `02:44.1`, `1:02.37` | Original text preserved; numeric seconds stored |
| `5.18m`, `4.92` under a field event | Original text preserved; numeric metres stored |
| `8.86q`, `52.61Q` | Preserved as UNPARSED with a preview warning; suffix meaning is not guessed |
| `30:87`, `1:12:34`, `.34.55`, `It's the other Ella` | Preserved as UNPARSED with a warning; no numeric comparison score |
| `0.352083333` | Exceeds supported numeric precision; retained as UNPARSED rather than assuming an Excel time conversion |
| `ADDED`, `in Roster`, `Not IN Roster` | Known capitalization normalized; original file preserved |
| Repeated athlete with missing DOB in one row | Reject that row; other rows with valid DOB can match, enrich the athlete and append performances |

All performance columns from this roster are **historical imports with no invented meet link**. `VALID` describes a parseable value, not official verification. Personal-best/season-ranking eligibility, qualifier suffixes, Excel serial-date interpretation, and Trackie identifiers still need the club's confirmation.

The compatibility `POST /api/athletes/import` runs preview and commits all rows through the same service. It retains athleteCount/eventCount/duplicateCount (unchanged rows) and adds updatedCount/skippedCount/failedCount. Like selected commit, it uses per-row transactions; it no longer has a separate whole-file legacy import service. No schema migration is needed for these import policy changes.

## Database and deployment

Hibernate manages the current schema through `spring.jpa.hibernate.ddl-auto=update`. Start with an empty MySQL database, let the backend create its tables from the JPA entities, then upload the client file through the import API. There is no previous deployed client schema to convert. The Flyway dependencies, SQL migrations, baseline settings and legacy-upgrade test have been removed at the project owner's request; [ADR-0006](adr/0006-hibernate-schema-management.md) records this simpler setup.

The entity mappings retain the required unique constraints, result precision and note-order index. Backend restarts keep current rows; the temporary MySQL mount clears them when the database container stops. Database verification uses disposable MySQL containers.

Production uses `SPRING_PROFILES_ACTIVE=prod` and requires `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. The initial production account has ADMIN role. This is a bootstrap access policy; club account provisioning and a login UI remain separate work. Serve production traffic over HTTPS. A write client first retrieves `/api/csrf` while authenticated and returns the provided token in its indicated header, retaining its session/cookie. Local development keeps the existing anonymous access policy.

## Verification

Current verification on 2026-10-26: **92 backend tests passed**, including Hibernate schema creation on empty MySQL, required-DOB rejection, different-DOB separation, identifier-required review and mixed race/jump/throw result retrieval. The removed legacy-upgrade test accounts for the change from the earlier 93-test suite. No frontend changes were made.

The backend suite covers independent participation, duplicate rejection, multiple attempts, bulk rollback, definition-only copy, note order/preservation, profile consistency, delete dependencies, import selection/retry, shared parsing, production authorization/CSRF, and entity-generated schema startup. Run from `backend`:

```sh
bash mvnw -Dapi.version=1.44 test
```

The Docker API override is needed for this machine's newer Docker installation and older Testcontainers client.
