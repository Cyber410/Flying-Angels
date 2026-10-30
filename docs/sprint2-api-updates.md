# Sprint 2 import, participation and results contracts

These backend changes implement the selected acceptance gaps. Frontend application files are unchanged. Existing athlete CRUD, individual result PUT, result creation, required import DOB and identifier-first matching remain supported.

## Import validation and repeated registrations

Both upload routes reject empty files and parsed files with no athlete records, including a header-only CSV. The response is 400 and no preview job is created. Existing CSV/TSV/XLSX support remains; this change does not introduce a CSV-only restriction.

`POST /api/athletes/import/{id}/commit` requires a non-empty `rowNumbers` list. An empty list is 400 rather than a successful no-op. Repeated row selections and numbers outside the job remain invalid.

Preview/get/commit rows now include nullable `repeatedOfRowNumber`:

```json
{
  "rowNumber": 3,
  "status": "DUPLICATE",
  "explanation": "Identical registration already appears at row 2; skipped",
  "repeatedOfRowNumber": 2
}
```

The example omits the existing athlete/externalId fields for brevity.

- A registration identity is the supplied external identifier when available; otherwise it is case-insensitive first/last name and exact DOB. Different DOBs without an identifier remain different people.
- An identical parsed registration repeated within one file is classified DUPLICATE and skipped, even if selected at commit. The first valid occurrence is the selectable canonical row. Selecting only its duplicate does not implicitly select the original.
- Repeated identities with different supplied information or additional performance values retain their normal NEW/UPDATE/UNCHANGED classification, carry a repeated-row reference and explanation, and remain reviewable. Eligible selected rows are rechecked in source order. This preserves the previously requested update and performance-history behavior.
- INVALID rows keep their validation errors and do not suppress a later valid registration. Existing AMBIGUOUS identity blocks are not bypassed by in-file repetition detection.
- DUPLICATE rows count as skipped, not failed. Imported/updated counts remain job-wide/cumulative.
- Existing name/DOB matches without a registration identifier remain blocked. Commit still revalidates database identity and differences immediately before saving; each row still has its own transaction.
- Cancelling after preview means not calling commit. Preview jobs remain stored, but athlete records are not created or updated.

The frontend review table should show DUPLICATE rows as excluded and display `repeatedOfRowNumber`/explanation for repeated identities. Use preview then selected commit for administrator review. The immediate endpoint remains available with the same validation and duplicate rules.

## Exact age and directory filtering

Age is completed years on the reference date, calculated from DOB. It advances on the birthday; a February 29 birth advances on March 1 in a non-leap year. Named group labels are not invented by the backend: the frontend maps agreed labels to inclusive `minAge` / `maxAge` bounds.

For athlete selection, supply the selected meet start date as `ageAsOf`:

```http
GET /api/athletes?search=Ada&gender=Female&minAge=12&maxAge=13&ageAsOf=2026-05-10
```

- Bounds can be supplied independently and must be between 0 and 150, with minAge <= maxAge.
- `ageAsOf` is required when either age bound is supplied. Missing/invalid reference dates or bounds return 400.
- Existing search/gender/team filters combine with age bounds. Response shape remains the existing athlete summary array.
- Unknown DOB or DOB after the reference date is excluded when age filtering is active. Such athletes remain visible without an age filter.
- Filtering runs in the database; the frontend does not need to download the directory and calculate ages itself.

## Participation removal

`DELETE /api/events/{eventId}/participants/{athleteId}` removes the assignment **and every result for that athlete in the selected event**. Results for other athletes or other events are preserved. This is the existing behavior, explicitly confirmed for this update.

The frontend confirmation should state that the event results will also be deleted. Deleting one result through the result-ID DELETE still leaves participation intact. Empty batches return 400, duplicate existing assignments return 409, and missing athletes return 404. Assignment batches roll back as a whole when one member fails. The same athlete can participate in multiple events.

## Edit All Results

The existing individual correction route remains:

```http
PUT /api/events/{eventId}/results/{resultId}
```

For an atomic batch correction, use the new route:

```http
PUT /api/events/{eventId}/results/bulk
Content-Type: application/json
```

```json
{
  "results": [
    {"resultId": 101, "result": "12.10", "unit": "SECONDS"},
    {"resultId": 102, "result": "12.20", "unit": "SECONDS"}
  ]
}
```

The response is 200 with the existing `EventResultResponse` array. Each row identifies an existing attempt; bulk editing preserves its ID, athlete, event, source and recordedAt. It does not append attempts. `score` is optional and, when supplied, must match the normalized result; `unit` is optional and must match the event definition.

The batch allows up to 500 non-null entries and requires at least one. Empty entries, repeated result IDs, blank values, wrong units and malformed values are 400. An unknown result or a result outside the target event is 404. Every correction is validated before changes are applied, and the operation is transactional: a failure leaves the whole batch unchanged. The bulk POST remains creation of additional attempts.

## Read-only Results Overview

```http
GET /api/results?search=Ada&meetId=1&categoryId=2&gender=Female&minAge=12&maxAge=13&sort=performance&page=0&size=25
```

| Parameter | Meaning |
| --- | --- |
| `search` | Case-insensitive substring of athlete first/last name; `%` and `_` are literal characters |
| `meetId` | One meet |
| `eventId` | One particular event instance within a meet |
| `categoryId` | Reusable event definition, allowing the same discipline across meets |
| `gender` | Case-insensitive exact match |
| `minAge`, `maxAge` | Inclusive ages calculated separately at **each meet's start date**, not its event date or today's date |
| `sort` | `date` (default, newest event date first), `athlete` (last/first name), or `performance` |
| `page`, `size` | Zero-based page (default 0), page size 1–100 (default 25) |

Filters combine with AND. Invalid IDs, bounds, sorting or paging return 400. Unknown positive filter IDs and unmatched searches return 200 with an empty page, rather than a resource-not-found error.

Response:

```json
{
  "content": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

Non-empty content rows contain result/athlete/meet/event/category IDs and names, gender, DOB, ageAtMeet, meetStartDate, eventDate, raw result, normalized score, unit, ranking direction, status and recordedAt.

Performance sorting groups by reusable event definition, then orders valid scores best first: ASC for race times, DESC for field distances/points according to the event's configured ranking order. Non-performance outcomes such as NT/FOUL follow scored results within their category. Ties have deterministic date/name/result-ID ordering. Scores from different disciplines are not ranked against each other.

All attempts are included, rather than only an athlete's best attempt. Historical imports without a meet remain in athlete history and are excluded from this competition overview; the API does not invent competition context for them. Unknown DOB appears with `ageAtMeet:null` unless an age filter excludes it.

## Verification

The JUnit suite covers empty/header-only uploads, empty commits, preview repetition and persistence, meaningful repeated updates, exclusions/cancellation, commit-time rechecks, birthday/leap-day boundaries, directory query combinations, participation rollback/removal scope, atomic bulk correction and overview filtering/sorting/pagination.

The Postman collection includes the new endpoints and fixtures. See [execution evidence](postman/verification.md) for the tested working tree, counts and actual results; do not treat collection syntax validation as an executed HTTP run.
