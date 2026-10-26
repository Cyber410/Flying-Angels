# Backend workflows: which endpoint to use and when

Use this guide to choose requests by the task you want to complete. You do not need to call every endpoint for every workflow. All paths are relative to `http://localhost:8080` in default local development.

## 1. Documentation map

| Question | Documentation |
| --- | --- |
| Which request do I use, in what order, and how do I check it? | This guide |
| What are the exact bodies, validation rules, architecture and database relationships? | [Backend project guide](../backend/README.md) |
| Where is the full endpoint inventory? | [API reference](../backend/README.md#11-complete-api-reference) |
| How do I run the bundled automated requests and upload fixtures? | [Postman test pack](postman/README.md) |
| Why did we choose these designs? | [Architecture Decision Records](adr/README.md), especially [import matching](adr/0003-single-import-module-and-upserts.md) and [preview/row transactions](adr/0004-persisted-previews-row-transactions.md) |

## 2. Choose the business operation

### Athlete directory and profile

| Task | Request | When to use it |
| --- | --- | --- |
| List or search athletes | `GET /api/athletes` | Directory view or verification after importing; supports search, gender and team filters |
| Read one athlete | `GET /api/athletes/{id}` | Full profile, participation and result context; verify saved changes here |
| Add one person manually | `POST /api/athletes` | A registration entered directly rather than through a spreadsheet |
| Edit one profile manually | `PUT /api/athletes/{id}` | Explicit profile editing; use the documented AthleteRequest body rather than assuming import blank-preservation rules apply |
| Delete one athlete | `DELETE /api/athletes/{id}` | Intentional record removal; review the [deletion behavior](../backend/README.md#deletion-behavior) first |

Use import endpoints for spreadsheet matching and create/update/skip decisions. Creating an athlete manually is a separate operation and can reject an existing identity as a conflict.

### Meet setup and reusable event definitions

A definition such as `100m` describes a reusable event category. A meet event represents that category offered at a particular meet, such as the 100m at a summer competition. Their IDs are different.

| Task | Request | When to use it |
| --- | --- | --- |
| Find existing competitions | `GET /api/meets` | Meet list or selection |
| Read a competition and its events | `GET /api/meets/{id}` | Meet details |
| Create, edit or delete a competition | `POST /api/meets`, `PUT /api/meets/{id}`, `DELETE /api/meets/{id}` | Manage meet name, dates and location |
| Find reusable definitions | `GET /api/event-categories` | Reuse an existing category before creating another |
| Create a reusable definition | `POST /api/event-categories` | Define name, ranking direction and measurement unit |
| Add one meet event | `POST /api/events` | Supply meet ID, category ID or new category name, event date and other required fields |
| Add several meet events | `POST /api/meets/{id}/events/bulk` | Configure several events together; the batch rolls back if one is invalid |
| Copy selected events into a meet | `POST /api/meets/{id}/events/copy` | Supply sourceMeetId and source eventIds; athlete assignments/results are not copied |
| List a meet's events | `GET /api/meets/{id}/events` | Populate a meet-specific event selector |
| Read an event within a selected meet | `GET /api/meets/{meetId}/events/{eventId}` | Also checks that the event belongs to that meet |
| List all meet events | `GET /api/events` | Cross-meet event listing |
| Read an event with participants/results | `GET /api/events/{id}` | Event detail view |
| Edit or delete a meet event | `PUT /api/events/{id}`, `DELETE /api/events/{id}` | Manage an existing event instance |

Typical order: create/select a meet -> find/create definitions -> add or copy meet events -> assign athletes -> record results. The backend prevents the same category from appearing twice in one meet.

### Participation, results and notes

| Task | Request | When to use it |
| --- | --- | --- |
| Assign one athlete to an event | `POST /api/events/{id}/participants` | Body contains athleteId; no performance is required |
| Assign several athletes | `POST /api/events/{id}/participants/bulk` | Body contains athleteIds |
| List assigned athletes | `GET /api/events/{id}/participants` | Entry list before or after results exist |
| Remove an assignment | `DELETE /api/events/{id}/participants/{athleteId}` | Also removes that athlete's attempts for this event |
| Add an attempt | `POST /api/events/{id}/results` | Create a new performance record with athleteId and result |
| Add attempts for several athletes | `POST /api/events/{id}/results/bulk` | Body contains a results array |
| Add an attempt using the participant URL | `PUT /api/events/{id}/participants/{athleteId}/result` | Compatibility route; this appends a result even though its method is PUT |
| Read all attempts | `GET /api/events/{id}/results` | Inspect the event's full result history |
| Correct a particular attempt | `PUT /api/events/{id}/results/{resultId}` | Edit the identified record instead of adding another attempt |
| Delete a particular attempt | `DELETE /api/events/{id}/results/{resultId}` | Participation remains assigned |
| Read the event leaderboard | `GET /api/events/{id}/leaderboard` | Best comparable result per athlete in this event |
| Add a timestamped note | `POST /api/athletes/{id}/notes` | Supply text and optional eventId for event-specific context |
| Read note history | `GET /api/athletes/{id}/notes` | Newest first, including event/meet information where applicable |

Assignments, attempts, general profile notes and timestamped note history are distinct records. The same athlete can enter several events and have several attempts in each. An event leaderboard is not a season-ranking or verified personal-best API; those services are not implemented.

### Spreadsheet import and authentication

| Task | Request | When to use it |
| --- | --- | --- |
| Review a spreadsheet before changing athletes | `POST /api/athletes/import/preview` | Recommended for the real client roster; multipart file and source |
| Reopen an uploaded job | `GET /api/athletes/import/{id}` | Read saved classifications/outcomes without uploading again |
| Apply selected records | `POST /api/athletes/import/{id}/commit` | JSON rowNumbers from the preview |
| Process an entire upload immediately | `POST /api/athletes/import` | Convenience alternative when row selection is unnecessary; multipart file and source |
| Obtain write-request CSRF metadata | `GET /api/csrf` | In authenticated mode, retrieve token/cookie before protected writes |

Default local development allows anonymous access. In secured mode all import routes require ADMIN; credentials and CSRF handling are described in the [security guide](../backend/README.md#14-security-and-cors) and [Postman instructions](postman/README.md#7-secured-mode-testing).

## 3. What preview actually does

Preview is a saved plan, not a completed athlete import.

1. Read the file and validate its headers/rows. Supported formats are CSV, TSV and XLSX; limits are 10 MB and 10,000 input rows.
2. Parse profile fields and historical performance columns.
3. Match each row against athletes already in the configured database.
4. Compare supplied nonblank values and performance history against that match.
5. Save the original uploaded bytes to `athlete_import_jobs` and row classifications to `athlete_import_rows`.
6. Return an importId and per-row status, explanation, athleteId, parsed athlete data and externalId.

Preview does write import-job records. It does not create/update athlete profiles, assignments, meet events or result records. A non-null athleteId in a preview identifies an existing matched athlete; it does not mean preview just created that person.

| Status | What it tells the reviewer | Next action |
| --- | --- | --- |
| NEW | No eligible existing athlete matched | Select to create |
| UPDATE | A matched athlete has changed supplied information | Review the changed-field explanation, then select to update |
| UNCHANGED | A confirmed identity has the same supplied information | No athlete write is needed |
| INVALID | A required value or another validation rule failed | Correct the file and create a new preview |
| AMBIGUOUS | An existing name/DOB match lacks an identifier, or identity remains unresolved | Supply the actual registration identifier and create a new preview |

First name, last name and a valid DOB in the past are required on every imported row, including updates to existing athletes. Missing, malformed, today or future DOB makes the row INVALID and prevents saving it. Missing gender is only a warning. Blank optional gender/status/note fields preserve existing profile values. This required-DOB rule replaced the earlier permissive import policy; see [ADR-0008](adr/0008-required-import-birth-dates.md).

Performance headers determine the measurement: races/hurdles/miles/steeplechase use seconds, while jumps/vaults/throws use metres. `2:44.07` becomes a comparable score of 164.07 seconds; `5.18m` becomes 5.18 metres. Athlete-profile event results return raw result, normalized score, unit and status. Incompatible or unrecognized source values get an expected-unit warning and remain UNPARSED without a numeric score.

Identity policy: the same name with a known different DOB represents a separate person and can be NEW. The same name and DOB as an existing athlete requires a supplied registration identifier before updating or skipping, even if only one existing athlete matches. Gender does not bypass this review. A supplied identifier can attach to one unkeyed exact name/DOB match; an existing source/identifier match takes priority and permits confirmed corrections. See [ADR-0009](adr/0009-name-dob-and-confirmed-identifiers.md).

The `athlete` object contains parsed input, not necessarily the complete stored profile. A blank gender in this object does not prove the saved athlete's gender is blank. Use the athlete GET endpoint to inspect stored data.

## 4. What commit actually does

The commit request must use the importId returned by the preview you reviewed. Send row numbers, not athlete IDs:

```http
POST {{baseUrl}}/api/athletes/import/{{realImportId}}/commit
Content-Type: application/json
```

```json
{
  "rowNumbers": [3, 6]
}
```

1. Check that row numbers belong to this job and are not repeated.
2. For selected NEW/UPDATE rows, lock the row and recheck the current database match/differences. Legacy VALID previews are also supported.
3. Create a new athlete or merge changed information into the matched athlete; append newly supplied historical performances.
4. Save one row's athlete/performance changes atomically and record IMPORTED or UPDATED with its athleteId. A row that no longer needs changes becomes UNCHANGED.
5. If a caught database/validation failure occurs during saving, roll back that row and record FAILED. Other successful rows remain saved.
6. Return all source rows plus imported, updated, skipped and failed counts.

Already-UNCHANGED rows are not written by commit. Already-IMPORTED/UPDATED rows are not applied again. INVALID and AMBIGUOUS selections do not save athletes and count as failures. Eligible rows excluded from a commit can be selected later; SKIPPED is a response-only label for those exclusions.

Successful HTTP 200 means the operation returned its outcome report. It does not promise an athlete was created or changed. A request containing only unchanged records can correctly return imported=0, updated=0 and failed=0.

Counts summarize all rows in the job. Imported/updated totals include earlier successes in that same job. Skipped includes unchanged rows and exclusions; it is not a count of invalid athletes or selected rows alone.

`GET /api/athletes/import/{id}` retrieves saved statuses and reparses the stored upload; it does not recompute every match against the latest database state. Selected eligible rows are rechecked during commit. For a fresh comparison, upload a new preview. Editing the CSV on your computer does not change an existing job's stored file.

## 5. Why the real client CSV can report zero writes

An actual client file can legitimately be unchanged. Real data is compared against the current database just like fixture data.

In the response discussed during testing, selected row 3 matched an existing athlete ID and was UNCHANGED. Selected row 6 also matched an existing athlete ID and was UNCHANGED. Therefore committing [3,6] produced no athlete writes. The response reported 440 skipped rows because counts covered the entire file, including unselected rows.

That earlier response predates the identifier-required matching rule. Under the current policy, the same unkeyed name/DOB matches are AMBIGUOUS until a registration identifier is supplied. Confirmed identifier matches can still be UNCHANGED.

This establishes that the matcher found equivalent supplied information in stored records. It does not establish when or by which request the records were originally populated. They may already be present from an earlier import, manual entry or migrated data. Check the active baseUrl/database if you expected an empty environment; do not delete the client's records to force a NEW result.

To verify the match, use each row's athleteId with `GET /api/athletes/{id}` and compare the supplied profile/performance values. A missing incoming field is ignored for updates, so matching does not require the spreadsheet to repeat every stored value.

The current response does not provide a field-by-field old/new diff or explain how the original athlete was first created. UPDATE explanations list changed field names. Use the profile GET response for current stored values.

## 6. Postman workflow for the real client file

### A. Create a separate manual workflow

Create a separate collection for client-file requests. The bundled automated collection is designed for synthetic fixtures, contains cleanup requests and creates a random source per run. Its fixed assertions are not a validation suite for a 440-row client file. A green Test Results tab is not a substitute for reviewing statuses and saved profiles.

Select the local environment and use the running backend's baseUrl. Set a stable source such as CLUB_ROSTER. Keep this source for later uploads from the same dataset. If continuing an existing job, its source was fixed at preview creation and is not changed by the commit body.

### B. Upload and inspect

Send `POST {{baseUrl}}/api/athletes/import/preview` with Body -> form-data:

| Key | Type | Value |
| --- | --- | --- |
| file | File | Select the real client CSV |
| source | Text | CLUB_ROSTER |

Let Postman generate the multipart Content-Type. Save the returned importId as `realImportId` in the active environment. You can do this manually, or add this request's post-response script:

```javascript
pm.test("Preview succeeded", function () {
    pm.response.to.have.status(200);
    const preview = pm.response.json();
    pm.expect(preview.importId).to.be.a("string");
    pm.environment.set("realImportId", preview.importId);
});
```

Review explanations and select the intended NEW/UPDATE row numbers. A first import into a database without matching athletes normally produces NEW records. Subsequent identical uploads with confirmed registration identifiers normally produce UNCHANGED; without identifiers, existing name/DOB matches are AMBIGUOUS. Repeated identities within one file are rechecked in source order during commit, so not every previewed NEW row necessarily becomes a separate athlete.

### C. Commit and verify

Send `POST {{baseUrl}}/api/athletes/import/{{realImportId}}/commit` with a JSON rowNumbers array. Use numbers returned by this preview, not numbers remembered from a fixture. The API accepts explicit selections, not a wildcard or an omitted array meaning all rows.

Inspect each selected row's status/explanation and athleteId. Then call `GET {{baseUrl}}/api/athletes/{athleteId}` to verify the stored profile/results. You can reopen the job with `GET {{baseUrl}}/api/athletes/import/{{realImportId}}`.

For an entire-file workflow without manual selection, `POST /api/athletes/import` runs preview and all-row commit internally. It returns counts rather than an importId/per-row report. It is an alternative to the reviewed workflow, not another required step afterward. See the [compatibility counter definitions](../backend/README.md#immediate-endpoint-and-compatibility); duplicateCount overlaps with unchanged skips and eventCount covers incoming entries on newly created rows.

### D. Prove updates and unchanged-repeat behavior

On a test copy of the client database/file:

1. Choose an athlete with a confirmed source/registration identifier and record their current athleteId and profile.
2. Change a nonblank field in a CSV copy, such as the NOTE, without changing their identity.
3. Create a new preview using the same source. Expect UPDATE and an explanation naming the changed field.
4. Commit that row using the new importId. Expect UPDATED and the same athleteId.
5. Read the athlete again and check the changed value.
6. Preview the edited file again. Expect UNCHANGED for that row.

An unchanged selection does not exercise the create/update save path. To test creation, use a separate synthetic new athlete in a test database; do not change a real athlete's name to manufacture a new identity.

## 7. Client-data limitations to review

- The supplied client header has no registration identifier column. Initial distinct name/DOB registrations can create athletes, but existing name/DOB matches are blocked until an actual identifier is supplied. Request registration identifiers from the client rather than inventing them. A known different DOB represents a different person, not a correction to an unkeyed name match.
- Source and externalId together identify keyed registrations. Changing source for later imports can prevent identifier matching; keep the same source for that dataset.
- Placeholder rows such as Person 1/Person 1 satisfy name validation. The parser cannot know they are examples; exclude them during review if the client confirms they are placeholders.
- Original source bytes are preserved, including unrecognized administrative columns. Unknown values and suspicious performance formats still need review; importing is not official-result verification.
- Roster performance columns have no confirmed meet context. They are stored as historical values and do not create meet participation or verified rankings/personal bests.
- Blank incoming cells preserve data. Replacing the general profile note with a nonblank NOTE is different from appending a timestamped note through the notes endpoint.

## 8. Where the implementation lives

All import HTTP routes are in [AthleteImportController](../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportController.java). The [service](../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportService.java) coordinates parsing, stored jobs and selection; the [matcher](../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportMatcher.java) decides NEW/UPDATE/UNCHANGED; the [row writer](../backend/src/main/java/com/flyingangels/backend/importing/ImportRowWriter.java) saves each eligible row in its own transaction. Ordinary profile operations remain in the athlete module. Both upload workflows share the same matching/writing rules.
