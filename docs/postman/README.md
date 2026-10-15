# Backend testing in Postman

This test pack covers **all 39 controller endpoints** and the main backend changes. It includes 115 ordered functional requests, 8 separate secured-mode requests, response assertions, captured IDs, and synthetic import files. No frontend changes are required.

For choosing endpoints by task and testing the real client CSV, read [Backend workflows](../backend-workflows.md). The bundled fixture assertions and cleanup are intended for synthetic data; use a separate manual collection for client records.

## Files

| File | Purpose |
| --- | --- |
| [flying-angels-backend.postman_collection.json](flying-angels-backend.postman_collection.json) | Full development/ADMIN functional run, including positive and negative paths |
| [flying-angels-security.postman_collection.json](flying-angels-security.postman_collection.json) | Authentication and CSRF checks; run only in secured mode |
| [local.postman_environment.json](local.postman_environment.json) | Local base URL and empty credential placeholders |
| [fixtures](fixtures) | Synthetic CSV, TSV and typed-date XLSX uploads |

## 1. Start the current backend

From the repository root, build/start the development stack:

```sh
docker compose up --build mysql backend
```

The default Postman URL is `http://localhost:8080`. Rebuild so you are testing the current import create/update rules rather than an older Docker image. See [backend setup](../../backend/README.md#16-running-and-building) for direct Java execution and database settings.

Use a **disposable test database** for this pack. Requests deliberately create, update and delete synthetic records. Cleanup deletes captured athlete/event/meet IDs, but categories and import jobs have no delete endpoints and remain. The first request generates a unique run ID/source to separate runs. Do not use these requests as a database-reset script.

## 2. Import into Postman

1. Import both collection JSON files and the environment JSON.
2. Select the **Flying Angels local** environment.
3. Set `baseUrl` to the current backend URL, with no trailing slash.
4. For ordinary local development, leave the main collection's Authorization as **No Auth**.
5. Keep Postman's cookie handling enabled.
6. Use the desktop app/agent to reach a backend on localhost.
7. Run the complete main collection in its displayed folder order, one iteration.

The first request resets IDs/token state and creates `runId` and `importSource`. Later response scripts save numeric athlete, meet, category, event and result IDs automatically. You do not need to type database IDs manually.

If you run one request individually, first execute its setup dependencies. For example, create the meet/category/event before assigning an athlete or recording a result. Running only a cleanup folder with stale variables is not a valid functional run.

### Upload paths

Files are referenced as `fixtures/...` relative to this directory. Set Postman's working directory to `docs/postman`, or open each upload request's **Body → form-data → file** field and select the corresponding fixture manually.

Leave the multipart `source` value as `{{importSource}}` for the entire run. Changing source mid-run changes external-ID matching. Do not manually set a multipart Content-Type header; Postman must generate its boundary.

## 3. What each folder proves

| Folder | Main checks |
| --- | --- |
| 00 — Setup and athletes | Create with missing DOB, directory fields/filtering, profile structure, invalid names/future CRUD dates, duplicate identity and malformed IDs |
| 01 — Meets and definitions | Date ranges and legacy meetDate, definition reuse/uniqueness, event CRUD, bulk creation rollback, event ownership and dates |
| 02 — Participation | Assign without a fake result, reject duplicate assignments, bulk rollback, same athlete in different events |
| 03 — Results | Multiple attempts, correction by result ID, mm:ss normalization, field metres, NT/FOUL statuses, unit/score validation, bulk rollback and best-attempt leaderboard |
| 04 — Notes/copy | Newest-first note history, meet context, validation, selected copying without source athletes/results |
| 05 — Imports | Missing/unusable DOB, selected create, later profile update, unchanged skip, blank preservation, retry, invalid/ambiguous rows, immediate route, TSV/XLSX |
| 06 — Deletion/cleanup | Result deletion preserves participation, removal deletes attempts, event/meet cascade, notes survive detached, athlete deletion and 404 |

Tests check both HTTP status and response values. A returned 200 alone is not sufficient evidence of correct updates or rollback.

## 4. Complete endpoint checklist

The following tables are generated from the existing backend API guide; every method/path has at least one request in the functional collection.

### Athletes

| Method | Path | Input | Success / response |
| --- | --- | --- | --- |
| GET | `/api/athletes` | search, gender, team query strings | 200; summary array |
| GET | `/api/athletes/{id}` | Athlete ID | 200; AthleteProfileResponse |
| POST | `/api/athletes` | AthleteRequest | 201; AthleteProfileResponse |
| PUT | `/api/athletes/{id}` | AthleteRequest | 200; AthleteProfileResponse |
| DELETE | `/api/athletes/{id}` | Athlete ID | 204 |

### Meets and reusable definitions

| Method | Path | Input | Success / response |
| --- | --- | --- | --- |
| GET | `/api/meets` | None | 200; MeetResponse array |
| GET | `/api/meets/{id}` | Meet ID | 200; `{meet, events}` |
| POST | `/api/meets` | MeetRequest | 201; MeetResponse |
| PUT | `/api/meets/{id}` | MeetRequest | 200; MeetResponse |
| DELETE | `/api/meets/{id}` | Meet ID | 204 |
| GET | `/api/meets/{id}/events` | Meet ID | 200; EventResponse array |
| GET | `/api/meets/{meetId}/events/{eventId}` | Both IDs | 200; EventResponse if event belongs to meet |
| POST | `/api/meets/{id}/events/bulk` | `{events: [EventRequest]}` | 201; EventResponse array |
| POST | `/api/meets/{id}/events/copy` | `{sourceMeetId, eventIds}` | 201; copied EventResponse array |
| GET | `/api/event-categories` | None | 200; EventCategoryResponse array |
| POST | `/api/event-categories` | `{name, rankingOrder, unit?}` | 201; EventCategoryResponse |

### Events, participation and results

| Method | Path | Input | Success / response |
| --- | --- | --- | --- |
| GET | `/api/events` | None | 200; EventResponse array |
| GET | `/api/events/{id}` | Event ID | 200; `{event, results, participants}` |
| POST | `/api/events` | EventRequest | 201; EventResponse |
| PUT | `/api/events/{id}` | EventRequest | 200; EventResponse |
| DELETE | `/api/events/{id}` | Event ID | 204 |
| POST | `/api/events/{id}/participants` | `{athleteId}` | 201; EventParticipationResponse |
| GET | `/api/events/{id}/participants` | Event ID | 200; participation array |
| POST | `/api/events/{id}/participants/bulk` | `{athleteIds: [...]}` | 201; participation array |
| DELETE | `/api/events/{id}/participants/{athleteId}` | Both IDs | 204 |
| GET | `/api/events/{id}/results` | Event ID | 200; EventResultResponse array |
| POST | `/api/events/{id}/results` | `{athleteId, result, score?, unit?}` | 201; EventResultResponse |
| POST | `/api/events/{id}/results/bulk` | `{results: [...]}` | 201; result array |
| PUT | `/api/events/{id}/results/{resultId}` | Result request | 200; corrected result |
| DELETE | `/api/events/{id}/results/{resultId}` | Both IDs | 204 |
| PUT | `/api/events/{id}/participants/{athleteId}/result` | `{result, score?, unit?}` | 200; newly appended result |
| GET | `/api/events/{id}/leaderboard` | Event ID | 200; best result per athlete |

### Notes, imports and security

| Method | Path | Input | Success / response |
| --- | --- | --- | --- |
| POST | `/api/athletes/{id}/notes` | `{text, eventId?}` | 201; AthleteNoteResponse |
| GET | `/api/athletes/{id}/notes` | Athlete ID | 200; notes newest first |
| POST | `/api/athletes/import/preview` | Multipart `file`, optional `source` | 200; ImportPreviewResponse |
| GET | `/api/athletes/import/{id}` | Import UUID | 200; ImportPreviewResponse |
| POST | `/api/athletes/import/{id}/commit` | `{rowNumbers: [...]}` | 200; ImportCommitResponse |
| POST | `/api/athletes/import` | Multipart `file`, optional `source` | 200; create/update/skip/failure count response |
| GET | `/api/csrf` | Authenticated context in secure mode | 200; CSRF token metadata/value |

## 5. Request bodies and ID variables

Every JSON request already contains its body in the collection. The main shapes are:

| Operation | Body fields |
| --- | --- |
| Athlete create/update | firstName, lastName, optional DOB/gender/note/statuses/legacy events |
| Meet create/update | name, startDate, endDate, location, optional externalUrl; legacy meetDate is supported |
| Definition create | name, rankingOrder (`ASC`/`DESC`), optional unit (`SECONDS`/`METRES`/`POINTS`) |
| Event create/update | name, eventDate, rankingOrder, meetId, categoryId or categoryName; optional metadata |
| Bulk meet events | `{ "events": [event objects] }` |
| Selected event copy | `{ "sourceMeetId": 1, "eventIds": [2,3] }` |
| One participation | `{ "athleteId": 1 }` |
| Bulk participation | `{ "athleteIds": [1,2] }` |
| One result | `{ "athleteId": 1, "result": "2:44.07", "unit": "SECONDS" }` |
| Result correction | Same result shape, with resultId in the URL |
| Bulk results | `{ "results": [result objects] }` |
| Note | `{ "text": "Observation", "eventId": 1 }`; omit eventId for a general history note |
| Import commit | `{ "rowNumbers": [2,4] }` |
| Upload | Multipart `file` and `source` fields |

Numeric IDs in saved bodies use unquoted variables, for example:

```text
{
  "athleteId": {{athleteAId}},
  "result": "12.40",
  "unit": "SECONDS"
}
```

A 400 caused by a literal `{{athleteAId}}` usually means setup did not run or a setup assertion failed. Open the earlier request's Test Results before changing application code.

## 6. Import fixture walkthrough

### Initial CSV

[athletes-initial.csv](fixtures/athletes-initial.csv) has four source records:

| Source row | Input | Initial preview |
| --- | --- | --- |
| 2 | Athlete with missing DOB, ID QA-101, historical 100m value | NEW |
| 3 | Missing last name | INVALID |
| 4 | Athlete with unusable `0000-00-00` DOB, ID QA-103 | NEW, unknown DOB warning |
| 5 | Valid athlete deliberately excluded | NEW |

Commit rows `[2,4]`: expect `imported=2`, `updated=0`, `skipped=2`, `failed=0`. Repeating that commit retains the same IDs and cumulative imported count; it does not insert two more people.

Selecting the invalid row separately reports a failed row while the two earlier successful imports remain stored.

### Later registration update

[athletes-updated.csv](fixtures/athletes-updated.csv) supplies the same external IDs with new DOB/status/note/performance information.

1. Preview should show UPDATE for both rows.
2. Commit `[2,3]`: expect `imported=0`, `updated=2`.
3. Retrieve the first athlete: DOB is filled, status/note are updated, and both old and new performance values remain.
4. Preview the updated file again: both rows should be UNCHANGED.
5. Commit unchanged rows: no new athlete or update; `skipped=2`.
6. [athletes-blanks.csv](fixtures/athletes-blanks.csv) supplies blanks against that profile: UNCHANGED, with no deletion of DOB/status/note.

### Other uploads

- [athletes-immediate.csv](fixtures/athletes-immediate.csv): immediate-route creation without DOB.
- [athletes-immediate-updated.csv](fixtures/athletes-immediate-updated.csv): same identifier, later update; repeating it skips unchanged.
- [athletes.tsv](fixtures/athletes.tsv): tab-delimited registration.
- [athletes.xlsx](fixtures/athletes.xlsx): workbook containing a typed date cell.
- [invalid-headers.csv](fixtures/invalid-headers.csv): missing required first/last headers, expect 400.
- [athletes-ambiguous.csv](fixtures/athletes-ambiguous.csv): used after creating two same-name people; expect AMBIGUOUS and no arbitrary update.

The ambiguous scenario is about multiple possible people, not a blanket rejection of missing DOB. Missing or unusable DOB alone does not discard an athlete.

Import counters are job-wide/cumulative. `VALID` performance status means parseable, not official verification. Imported roster performances have no invented meet ID and are not automatically eligible personal bests/rankings.

## 7. Secured-mode testing

Do not run the security collection against anonymous development mode: its expected security rejection responses would correctly fail there.

Enable secured mode through the prod configuration, supplying its required account/database variables, or enable the equivalent security property in an isolated test runner. See [security](../../backend/README.md#14-security-and-cors).

Set environment `username`/`password` **locally** to the bootstrap ADMIN credentials. Keep real passwords out of exported shared files. The security collection has 8 ordered checks:

1. Anonymous GET is rejected with 401.
2. Incorrect Basic credentials are rejected with 401.
3. Valid ADMIN obtains a CSRF token and cookie.
4. POST with Basic credentials but no CSRF token is rejected with 401 in the current live configuration.
5. POST with Basic credentials and an invalid CSRF token is likewise rejected with 401.
6. Authenticated POST with the retrieved token succeeds with 201.
7. ADMIN deletes that created athlete with the token, expecting 204.
8. Its authenticated profile GET returns 404.

The live Basic-auth requests above return 401 on CSRF failure; the pre-authenticated MockMvc tests return 403. This is an observed distinction between these request contexts, so do not treat a valid username/password alone as evidence that a write will succeed.

Keep cookies enabled between token retrieval and writes. The response's `headerName` determines the token header; use the returned token rather than copying the cookie manually.

To run the **main** functional collection in secured mode, set its collection Authorization to Basic Auth with `{{username}}`/`{{password}}`. Its CSRF request captures the token and its collection pre-request script attaches the header to writes.

Only the bootstrap ADMIN account is provisioned by current prod properties. Postman cannot log in as an unimplemented COACH/VIEWER user. Role-denial behavior is covered by [ProductionSecurityTest](../../backend/src/test/java/com/flyingangels/backend/config/ProductionSecurityTest.java); add real test identities before doing equivalent manual role tests.

## 8. What HTTP tests do not prove

The collection demonstrates API-visible outcomes. Continue running the JUnit suite for:

- Original-schema migration/backfill and generated-index handling.
- Injected database/write failures and rollback of partially updated profiles.
- Role checks using controlled test principals.
- Cases not represented by the bundled fixtures, including concurrency and larger datasets.

A selected INVALID import row proves that valid rows can still be committed; it does not simulate a database failing midway through a valid row. The injected-failure JUnit tests cover that separate condition.

## 9. Test evidence for QAP and presentations

Save the Collection Runner summary and link it to the sprint card/PR. Record:

- Backend commit or branch and active profile.
- Test database/environment and base URL (without passwords).
- Run date, request/assertion totals, failures and follow-up fixes.
- Use-case/scenario reference and a screenshot or report for each demonstrated workflow.

Example traceability:

| Use case | Sunny-day evidence | Rainy-day evidence |
| --- | --- | --- |
| Meet management | Multi-day create/update/retrieve | Reversed dates; narrowing dates around existing events |
| Event management | Bulk add and selected copy | Duplicate category; bulk rollback; wrong meet ownership |
| Participation | Assign/bulk/list; same athlete in two events | Duplicate assignment; rollback of a partially valid batch |
| Results | Multiple attempts, correction, track/field normalization | Wrong unit/score/format; invalid bulk rollback |
| Notes | Newest-first retrieval and meet context | Empty text rejected; deletion detaches context but preserves text |
| Athlete import | Missing DOB create, later update, unchanged skip | Missing required name/header; ambiguous identity; excluded selection |
| Athlete profile | Required fields and result/participation context | Malformed/deleted ID and duplicate demographics |

ADRs explain design rationale rather than test execution. Link the relevant [decision record](../adr/README.md) beside implementation and test evidence.

## 10. Optional command-line execution

With Node/npm and Newman available, run from this directory:

```sh
npx newman@6 run flying-angels-backend.postman_collection.json \
  -e local.postman_environment.json \
  --working-dir . \
  --reporters cli,json,junit \
  --reporter-json-export /tmp/flying-angels-postman.json \
  --reporter-junit-export /tmp/flying-angels-postman.xml
```

For the security collection, supply local credentials through a private environment file; do not commit an exported environment containing passwords. This pack does not require changes to the frontend or installation of a backend application dependency.

See [verification.md](verification.md) for the execution evidence collected while preparing this pack.
