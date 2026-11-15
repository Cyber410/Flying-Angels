# Flying Angels Backend — Project and Architecture Guide

This document describes the backend as implemented in this repository. It is the main guide for understanding the project, running it, extending it, and integrating a client with its API. Examples use illustrative IDs and local development settings.

For a task-oriented explanation of which endpoint to call and when, start with [Backend workflows](../docs/backend-workflows.md). It includes the real-client CSV process, what preview stores, what commit writes, and why a successful commit can report zero changes.

The backend manages athletes, meets, reusable event definitions, event participation, performance attempts, athlete notes, and spreadsheet imports. It is a Spring Boot application backed by MySQL. The React frontend calls its HTTP endpoints under `/api`.

## Contents

1. [Scope and terminology](#1-scope-and-terminology)
2. [Technology stack](#2-technology-stack)
3. [Architecture and design](#3-architecture-and-design)
4. [Project structure and class responsibilities](#4-project-structure-and-class-responsibilities)
5. [Database model and relationships](#5-database-model-and-relationships)
6. [Athlete management and profiles](#6-athlete-management-and-profiles)
7. [Meet and event management](#7-meet-and-event-management)
8. [Participation and results](#8-participation-and-results)
9. [Athlete notes](#9-athlete-notes)
10. [Spreadsheet import design](#10-spreadsheet-import-design)
11. [Complete API reference](#11-complete-api-reference)
12. [Validation and error handling](#12-validation-and-error-handling)
13. [Transactions, concurrency, and fetching](#13-transactions-concurrency-and-fetching)
14. [Security and CORS](#14-security-and-cors)
15. [Configuration and environment variables](#15-configuration-and-environment-variables)
16. [Running and building](#16-running-and-building)
17. [Database schema management](#17-database-schema-management)
18. [Testing and verification](#18-testing-and-verification)
19. [Frontend integration](#19-frontend-integration)
20. [Troubleshooting](#20-troubleshooting)
21. [Current limitations and extension points](#21-current-limitations-and-extension-points)
22. [Design decisions and maintenance guide](#22-design-decisions-and-maintenance-guide)
23. [Related documentation and diagrams](#23-related-documentation-and-diagrams)

## 1. Scope and terminology

### Implemented capabilities

| Area | Current behavior |
| --- | --- |
| Athletes | Create, search, retrieve, update, and delete athletes; return demographic fields and team statuses |
| Meets | Create and manage competitions with start/end dates; retrieve their events |
| Event definitions | Create/list reusable categories such as 100m or Long Jump, including measurement unit and ranking direction |
| Meet events | Create individual or multiple events, prevent duplicate categories within a meet, copy selected events from another meet |
| Participation | Assign one or many athletes, list participants, remove an assignment |
| Results | Record multiple attempts, retrieve/correct/delete records, save batches, calculate an event leaderboard |
| Notes | Append timestamped notes, optionally linked to a meet event, and retrieve newest first |
| Imports | Parse CSV/TSV/XLSX, preview new/update/unchanged/invalid rows, import selected rows, and report outcomes |
| Meet exports | Download registration assignments or every recorded meet performance as CSV, with Admin/Coach permissions |
| Operations | Flyway migrations, Hibernate schema validation, automated tests, configurable authenticated production access |

### Updated Sprint 3 plan: UC-07 through UC-12

The updated **Flying Angels - Sprint 3 Use Cases and Implementation Plan** adds these backend requirements to the earlier six use cases. Frontend screens remain outside this implementation.

| Use case | Updated requirement and implementation |
| --- | --- |
| UC-07 | Admin/Coach/Athlete login accounts, one account per athlete record, invitation email, one-time password setup and resend. Account/ownership changes invalidate existing sessions. |
| UC-08 | Athlete logins can read their own competition history, calculated personal bests and progression through GET `/api/portal/history`; staff Notes remain inaccessible. |
| UC-09 | Configurable age bands and an explicit ageAsOf reference, shared personal-best/tie rules, and Athlete ranking access across all events and meets. Meet/event selectors and ranking context are visible without a nomination; other athletes' private profiles and staff information stay protected. Season requests are rejected until attribution is configured. |
| UC-10 | My Meets includes nominations only. PENDING is the initial status. Replies close at meet start, using the configured timezone and optional start time, and identical retries are idempotent. Guardian use shares the Athlete account/audit identity. |
| UC-11 | Staff availability rows include assigned events and latest response/audit context, plus full selected-meet counts. WHATSAPP joins the external response channels; no automated reminders are sent. |
| UC-12 | Both CSV types include age_group and age_as_of. Filenames include a sanitized meet name; generation failures have an explicit retryable error. |

The earlier `AWAITING_RESPONSE` filter remains an accepted alias for PENDING. `availabilitySummary.pending` is the canonical count; `awaitingResponse` remains a compatibility alias. Parent/guardian is not a separate role/entity. Athlete/parent access requires an invited, activated Athlete account. All new account API requests use invitations; supplying a password during account creation is rejected. The first Admin is bootstrapped only when no accounts exist.

### Important names

- **Athlete:** the person represented by a roster/profile record.
- **Meet:** a competition containing event instances, possibly over multiple days.
- **EventCategory:** the Java name for a reusable event definition, such as 100m. This name is retained for compatibility.
- **Event:** the Java name for a particular category offered within a meet. It is the meet-event instance.
- **EventParticipation:** an athlete's assignment to an event, independent of whether they have a performance.
- **EventResult:** an individual performance record. Several records may share the same athlete and event.
- **AthleteEvent:** the older athlete-owned event/value record retained for compatibility with existing roster data.
- **Historical import:** a spreadsheet performance with no confirmed meet association. The backend does not invent one.
- **Leaderboard:** best comparable attempt per athlete for one event. This is distinct from season rankings or verified personal bests.

Personal bests, progression data, central rankings and persisted login accounts are implemented below. Season attribution, official-result verification and a Trackie connector remain outside the implemented scope.

## 2. Technology stack

Versions below are taken from [pom.xml](pom.xml) and the repository's runtime configuration.

| Technology | Purpose |
| --- | --- |
| Java 17 language target | Backend source and compilation target in Maven |
| Spring Boot 4.1.1 | Application startup, configuration, web and persistence integration |
| Spring Web MVC | REST controllers and JSON request/response handling |
| Spring Data JPA / Hibernate | Entity persistence, repositories, schema validation, entity graphs, dirty checking |
| Spring Security | Staff-session authentication, role authorization, CSRF and scoped athlete access |
| Jakarta Validation | DTO field validation and nested request validation |
| MySQL 8.4 | Relational database used by Docker Compose and integration tests |
| Apache Commons CSV 1.14.1 | CSV/tab-delimited parsing and RFC 4180 CSV export writing |
| Apache POI OOXML 5.4.1 | XLSX workbook parsing and typed Excel date handling |
| Lombok | Generated getters, constructors, and dependency-injection constructors |
| Maven Wrapper | Consistent Maven invocation without a separate Maven installation |
| JUnit, Mockito, Spring test / MockMvc | Unit tests, HTTP contract tests, and service testing |
| Testcontainers 1.21.3 | Disposable real MySQL databases for startup and integration tests |

The local `backend/DockerFile` uses Eclipse Temurin JDK 21 and Maven's `spring-boot:run` for development, while Maven targets Java 17. The deployment `deploy/backend.Dockerfile` uses a multi-stage build: Java 17 JDK plus Maven Wrapper compile the application, and a Java 17 JRE runs only the resulting JAR as UID 10001. Maven, source files and build caches are absent from the deployed runtime.

## 3. Architecture and design

### Deployment architecture

The backend is a **modular monolith**: one deployable Spring Boot application, with packages organized around features. The packages share one database and can participate in the same transaction.

```text
React frontend or HTTP client
            |
            | HTTP requests / JSON / multipart uploads
            v
Spring Boot backend, port 8080
  Security filter chain
       -> MVC controller
       -> application/domain service
       -> Spring Data repository
       -> Hibernate/JDBC
            |
            v
MySQL, port 3306
```

Docker Compose runs frontend, backend, and MySQL as separate services. Containers connect to MySQL using the service hostname `mysql`; a backend running directly on the host normally uses `localhost` instead.

### Layer responsibilities

| Layer | Responsibility | Examples |
| --- | --- | --- |
| Controller | Bind HTTP paths and inputs, apply request validation, select status codes, return response DTOs | `MeetController`, `EventController`, `AthleteImportController` |
| Service | Enforce business rules, coordinate repositories, define transaction boundaries | `AthleteService`, `MeetService`, `EventService`, `AthleteImportService` |
| Repository | Execute queries and persist entities | `AthleteRepository`, `EventResultRepository` |
| Entity | Represent stored state and relationships; apply local state updates | `Meet.updateFrom()`, `EventResult.update()` |
| DTO | Define the external request/response contract | `MeetRequest`, `AthleteProfileResponse` |
| Shared infrastructure | Configure security/CORS, normalize API errors, configure versioned schema migrations | `SecurityConfig`, `ApiExceptionHandler`, Flyway configuration |

Services generally return entities that controllers map using DTO `static from()` methods. Athlete profile assembly and note operations return their response objects from services. There is no separate mapper framework or generic mapper layer.

### Example request flow

For `POST /api/events/{id}/results`:

1. Security evaluates authentication, roles, and CSRF when authenticated mode is enabled.
2. `EventController` binds and validates `EventResultRequest`.
3. `EventService.addResult()` loads the event and athlete.
4. `ResultValues` parses the performance using the category's unit and checks any supplied score.
5. The service creates participation if it does not already exist.
6. A new result is inserted without replacing earlier attempts.
7. `EventResultResponse.from()` exposes the result plus athlete/category/meet/event context.
8. A successful request returns HTTP 201.

## 4. Project structure and class responsibilities

```text
backend/
  pom.xml
  mvnw / mvnw.cmd
  .mvn/                            Maven wrapper support
  DockerFile                       Container runner
  README.md                        This guide
  src/
    main/
      java/com/flyingangels/backend/
        BackendApplication.java
        api/                       Shared error response and exception handling
        config/                    Security, CSRF endpoint, CORS
        athlete/                   Athlete lifecycle, profiles and athlete lifecycle
          dto/
        meet/                      Meets and meet-event batch/copy endpoints
          dto/
        event/                     Definitions, meet events, participation, results
          dto/
        note/                      Timestamped athlete notes
        importing/                 Persisted preview/commit import workflow
      resources/
        application.properties
        application-prod.properties
    test/java/com/flyingangels/backend/
      athlete/
      event/
      importing/
      config/
      DatabaseIntegrationTest.java
      ManagementIntegrationTest.java
      BackendApplicationTests.java
```

### Entry point and shared components

| Class | Responsibility |
| --- | --- |
| `BackendApplication` | Starts Spring Boot and component scanning under `com.flyingangels.backend` |
| `ApiErrorResponse` | Error envelope containing timestamp, HTTP status, code, message, and path |
| `ApiExceptionHandler` | Maps application, validation, parsing and persistence exceptions to HTTP errors |
| `SecurityConfig` | Configures development access and authenticated role/CSRF rules |
| `WebConfig` | Allows configured development browser origin and HTTP methods for `/api/**` |
| `CsrfController` | Exposes the current CSRF token for authenticated clients |

### Feature classes

| Feature | Main classes and responsibilities |
| --- | --- |
| Athlete | `AthleteController` exposes CRUD/search. `AthleteService` owns CRUD, matching and profile assembly. `AthleteRepository` owns search/identity queries. `Athlete` stores the profile; `AthleteEvent` stores legacy values. |
| Meet | `MeetController` exposes CRUD and event retrieval. `MeetService` validates ranges and manages deletion. `MeetRepository` fetches meets and their event context. `MeetEventController` delegates bulk/copy operations to `EventService`. |
| Event definition | `EventCategoryController`, `EventCategoryService`, `EventCategoryRepository`, and `EventCategory` create, list and resolve reusable definitions. |
| Meet event | `EventController`, `EventService`, `EventRepository`, and `Event` manage instances inside meets. |
| Participation | `EventParticipation` and its repository provide independent assignment state; the event service/controller expose its operations. |
| Result | `EventResult`, `EventResultRepository`, `ResultValues`, `ResultUnit`, `ResultStatus`, and `RankingOrder` represent, normalize and compare performances. |
| Rankings | `RankingsController` and `PortalRankingsController` expose staff/scoped projections. `RankingsService` assigns shared positions and pages best-athlete rows. `RankingQueryRepository` selects one canonical best result per athlete. `CompetitionResultPolicy` shares eligibility with athlete history. |
| Availability | `AvailabilityService` connects assignments to meet-level requests, validates versioned responses and appends audit records. Staff and portal controllers expose separate permissions and scope. |
| Meet exports | `MeetExportController` exposes options and downloads. `MeetExportService` reads meet-scoped assignments/results and writes CSV. `MeetExportType` and `MeetExportOptions` define choices and record counts. |
| Notes | `AthleteNoteController`, `AthleteNoteService`, `AthleteNoteRepository`, and the note entity/DTOs handle append-only note creation and ordered retrieval. |
| Import | `AthleteRegistrationParser` reads source data. `AthleteImportService` owns both upload routes, previews and commits. `AthleteImportMatcher` decides create/update/no-change. `ImportRowWriter` provides isolated row transactions. `ImportJob`/`ImportRow` and their repositories persist source bytes and outcomes. |

Request DTOs represent individual operations and batch wrappers. Response DTOs expose summaries, profiles and workflow outcomes rather than directly serializing JPA relationships.

## 5. Database model and relationships

### Stored tables

| Entity / table | Main stored fields | Purpose and constraints |
| --- | --- | --- |
| `Athlete` / `athletes` | ID, first/last names, general note, FLY/FLYA statuses, DOB, gender, import key, external source/ID | Primary person record; import key unique; external source/ID pair unique |
| `AthleteEvent` / `athlete_events` | ID, athlete ID, event name, raw result | Compatibility/history values owned by the athlete |
| `Meet` / `meets` | ID, name, legacy meet date, start/end dates, location, external URL | Parent competition |
| `EventCategory` / `event_categories` | ID, unique name, ranking direction, measurement unit | Reusable event definition |
| `Event` / `events` | ID, name, date, location, event type, URL, meet/category IDs, ranking direction | Unique `(meet_id, category_id)` |
| `EventParticipation` / `event_participations` | ID, event ID, athlete ID | Unique `(event_id, athlete_id)` |
| `EventResult` / `event_results` | ID, optional event ID, category/athlete IDs, optional result label, raw result, decimal score, unit/status, recorded time, source | Multiple rows for an athlete/event are allowed; numeric score is `DECIMAL(12,4)` |
| `AthleteNote` / `athlete_notes` | ID, athlete ID, optional event ID, text, created time | Indexed by athlete/time/ID for ordered retrieval |
| `ImportJob` / `athlete_import_jobs` | UUID, filename, source, original file bytes, created time | Original upload stored as a large binary object |
| `ImportRow` / `athlete_import_rows` | ID, job ID, source row number, status, explanation, imported athlete ID, repeated source-row number | Unique `(job_id, file_row_number)` |

`ImportRow.athleteId` is an outcome reference stored as a scalar value, not a JPA athlete relationship. There is no endpoint that automatically deletes jobs when an athlete is deleted.

### Relationship layout

```text
Meet 1 -------------------- many Event
EventCategory 1 ----------- many Event
Athlete 1 ----------------- many EventParticipation
Event 1 ------------------- many EventParticipation
Athlete 1 ----------------- many EventResult
Event 0..1 ---------------- many EventResult
EventCategory 1 ----------- many EventResult
Athlete 1 ----------------- many AthleteEvent
Athlete 1 ----------------- many AthleteNote
Event 0..1 ---------------- many AthleteNote
ImportJob 1 --------------- many ImportRow
```

Participation and results correspond through their **event/athlete pair**. The current result entity does not have a `participation_id` foreign key. Service methods maintain assignment existence when recording results. This distinction matters when extending the model or writing direct SQL.

An imported result can have a null event ID, but still has a category and athlete. It therefore represents a historical category-level performance, not confirmed participation in a particular meet.

### Deletion behavior

| Operation | Effect |
| --- | --- |
| Delete one result | Removes that result; participation remains |
| Remove a participant | Removes their assignment and their result attempts for that event |
| Delete an event | Removes its results and participation; note text survives with the event link detached |
| Delete a meet | Removes its child events, their results/participation; note links are detached |
| Delete an athlete | Service deletes their notes, central results and assignments, then the athlete and legacy values |

These are physical deletions. The project does not currently implement archival, soft deletion, audit revisions, or result correction history.

## 6. Athlete management and profiles

### Search

`GET /api/athletes` accepts optional `search`, `gender`, and `team` query parameters, defaulting to empty strings. It also accepts inclusive `minAge`/`maxAge` bounds with a required `ageAsOf` reference date when either bound is used. Age is completed years on that date; unknown DOBs are excluded only by active age filters. See [Sprint 2 API contracts](../docs/sprint2-api-updates.md).

- Search matches a case-insensitive substring of the concatenated first/last name.
- Gender is a case-insensitive exact filter.
- The service trims search/filter inputs and uppercases the team parameter.
- Results sort by last name, then first name, case-insensitively.
- No pagination is currently implemented.

The current `FLY`/`FLYA` team query checks whether the corresponding status is non-null and non-empty. It does **not** interpret negative values such as `Not In Roster` as non-membership. Treat the filter as a populated-status filter until club membership semantics are formalized.

### Create and update payload

```json
{
  "firstName": "Example",
  "lastName": "Athlete",
  "dateOfBirth": "2010-03-21",
  "gender": "Female",
  "flyStatus": "In Roster",
  "flyaStatus": "",
  "note": "General profile note",
  "events": []
}
```

First/last names are required. For ordinary athlete CRUD, DOB is optional but, when provided, must be in the past. File imports require a valid DOB in every incoming row. Duplicate checking for normal create/update uses name, DOB and gender when a DOB exists. It is a service check rather than a unique demographic database constraint; it is not a universal identity guarantee.

Updates are not general-purpose JSON PATCH operations. The same `AthleteRequest` shape is used for create and PUT:

- Omitting `note`, or sending null, preserves the current general note; an empty string clears it.
- Omitting legacy `events`, or sending null, preserves that collection.
- Supplying `events` replaces the legacy collection; an empty array clears it.
- Missing statuses normalize to empty strings.
- Other supplied profile values are applied through `Athlete.updateFrom()`; do not assume omitted demographic fields are preserved.
- Legacy event edits do not modify central result attempts. Use dedicated result endpoints for that data.

Known team-status spelling is canonicalized to `In Roster`, `Not In Roster`, `Not On Team`, or `Added`; unknown status text is retained. Known gender values normalize to `Male`/`Female`, including `Men`/`Women`. Unknown gender text is retained; import previews warn about it.

### Response shapes

The directory returns `AthleteSummaryResponse`: `id`, `firstName`, `lastName`, `dateOfBirth`, `gender`, `flyStatus`, `flyaStatus`.

Profiles return these fields plus `note`, `events`, and `participations`:

- `events[]` contains central result responses and any unmatched legacy values.
- `participations[]` contains independent assignments with athlete, event, meet and category context.
- A participation can exist with no result.
- Historical results can exist without a meet participation.

GET, create and update profile responses are assembled through `AthleteService`, so updates retain central results and participation in their response.

For display, a legacy value is suppressed if a central result has the same category name, ignoring case, and the same raw result string. This does not delete either stored record or prove that the two values are the same real-world performance.

## 7. Meet and event management

### Meet date design

```json
{
  "name": "Spring Meet",
  "startDate": "2026-05-16",
  "endDate": "2026-05-18",
  "location": "Example Stadium",
  "externalUrl": "https://example.com/meet"
}
```

`startDate` is required unless the compatibility `meetDate` field supplies it. An omitted `endDate` defaults to the effective start date. End must not precede start. If both `meetDate` and `startDate` are present they must agree.

Optional `startTime` is an ISO local time such as `10:00:00`; omitted/null means midnight. Availability closes at that time on startDate in the configured meet timezone. Start/end date categorization remains separate: an in-progress multi-day meet stays in Upcoming until its end date.

The entity retains `meetDate` as the effective start date. Updating a meet cannot leave existing event dates outside its new range.

### Meet views and centralized availability

Admins and Coaches can use these read-only APIs to find competitions and outstanding availability responses:

| Endpoint | Filters and response |
| --- | --- |
| GET `/api/meets` | `view=UPCOMING\|PAST\|ALL`, optional `search`; existing array shape plus an `availabilitySummary` on every meet |
| GET `/api/availability` | Optional `meetId`, `status`, `view`, `search`, `page` and `size`; paginated requests across meets |
| GET `/api/availability/{id}` | Request details and assigned events, using the request ID from the overview |

`view` defaults to ALL on both collection endpoints. Calling `/api/meets` without filters preserves the existing array response and newest-start-date-first ordering. UPCOMING includes ongoing meets and meets ending today, ordered by start date ascending. PAST includes meets whose end date is before today, ordered by start date descending; ALL also orders by start date descending. Meet names (case insensitive) and IDs stabilize equal-date meet ordering. Date calculations use the same `APP_AVAILABILITY_TIME_ZONE` clock as portal availability (default `America/Toronto`). Legacy records fall back from endDate to startDate to meetDate. Unknown dates appear only in ALL, after dated meets.

Meet search matches name or location. Centralized availability search matches the athlete's full name or meet name. Searches trim surrounding whitespace and ignore case; `%`, `_` and `!` are matched literally. Blank search clears the filter; values over 200 characters return 400. Filter names and enum values use the exact casing shown above.

Each meet has the following summary, including zero counts for meets without assigned athletes:

```json
{
  "availabilitySummary": {
    "total": 3,
    "pending": 1,
    "available": 1,
    "unavailable": 1
  }
}
```

Counts represent active athlete/meet requests. An athlete assigned to several events contributes once. Old confirmations invalidated by assignment changes count under awaitingResponse, rather than their previous answer. Removing the last assignment excludes that request from counts and overview lists while preserving its audit history. Counts derive from the same request state used by Meet Details and portal replies, without copying results or counting confirmation history.

For follow-up, use `GET /api/availability?view=UPCOMING&status=PENDING&meetId=12&page=0&size=25`. The selected-meet workflow supplies meetId; omitting it retains the existing cross-meet overview. Status accepts PENDING, AVAILABLE or UNAVAILABLE. The response has `filters` (including the server's `asOf` club date), `content`, `page`, `size`, `totalElements`, `totalPages` and `summary`. Each row includes identity, meet dates, effective status, assignedEventCount, assigned `events`, responseDeadline/canRespond, versions and `latestResponse` audit context, including staff-only remarks. Summary counts cover all active nominees in the selected meet, independent of the status/search/page filter; with no meet selected they cover all active requests. Select a row ID to review its full detail or record an external response. This API sends no reminders; athletes respond through their authenticated accounts.

Availability filters run together in the database before pagination. Page defaults to 0, size to 25, with size restricted to 1..100. Rows follow the selected meet-view date direction, then meet ID, case-insensitive athlete last/first name and request ID. A page beyond the end returns empty content with the actual filtered totals. No matching meets return an empty array; no matching requests or no outstanding replies return empty content and zero totals. A positive but unknown meetId returns 404; nonpositive/malformed IDs, invalid enum values, invalid paging or an unsupported numeric offset return 400. Past requests remain visible when selected and have canRespond=false.

Meet listing uses grouped availability/event counts without loading every event, participant or result collection. The availability page fetches its athlete/meet context together. Both APIs require Admin or Coach; anonymous requests receive 401 and portal/unsupported roles receive 403. Reading these views does not create requests, change versions or record responses.

Implementation: [MeetOverviewService](src/main/java/com/flyingangels/backend/meet/MeetOverviewService.java), [MeetOverviewRepository](src/main/java/com/flyingangels/backend/meet/MeetOverviewRepository.java), [MeetView](src/main/java/com/flyingangels/backend/meet/MeetView.java), [AvailabilityOverviewService](src/main/java/com/flyingangels/backend/availability/AvailabilityOverviewService.java) and [AvailabilityOverviewRepository](src/main/java/com/flyingangels/backend/availability/AvailabilityOverviewRepository.java).

### Meet CSV exports

Admins and Coaches can download the selected meet's registration list or competition results. These read-only operations work for past, current and future meets.

| Endpoint | Contract |
| --- | --- |
| GET `/api/meets/{id}/exports` | JSON options with meet ID/name and each type's displayName, recordCount and available flag |
| GET `/api/meets/{id}/export?type=REGISTRATION` | Download one CSV row per athlete-event assignment |
| GET `/api/meets/{id}/export?type=RESULTS` | Download one CSV row per recorded performance |

Read the options to review the selected type, then call its download endpoint when confirmed. Changing the selection only changes the type parameter. Cancelling requires no request; no export job or persistent file is created. Counts reflect the records at the time options were read; a later download reads current data and checks again whether records exist.

Registration includes all assignments, including Pending and Unavailable athletes, with their current meet-level `availability_status`. An athlete assigned to two events has two rows. Several attempts in one event do not duplicate a registration row. If an assignment change invalidates an earlier answer, its rows show PENDING. Archived athletes' existing assignments remain included.

Results include every result row belonging to the meet, preserving repeated attempts with identical values and labels such as Heat 1, Final and Attempt 2. VALID, DNS, DNF, NT, DQ, FOUL, PENDING and UNPARSED records remain visible; missing scores are empty cells. Results are not reduced to personal bests or leaderboard rows. A linked legacy result without a participation row is still exported, while imported values without a linked meet/event are excluded. Assignments without results contribute registration rows only. Updates and deletions through the normal APIs appear immediately in the next download.

Both CSV types start with these columns, in order:

```text
meet_id,meet_name,meet_start_date,meet_end_date,meet_location,event_id,event_name,event_date,category_id,category_name,athlete_id,first_name,last_name,date_of_birth,gender,age_group,age_as_of
```

| Type | Additional columns, in order |
| --- | --- |
| REGISTRATION | participation_id,availability_status |
| RESULTS | result_id,result_label,result,score,unit,status,recorded_at,ranking_order |

Both CSVs include age_group and age_as_of. When club bands are configured, supply an explicit `ageAsOf=YYYY-MM-DD`; omission returns 400. The same reference date classifies every row in either file. Without configured bands, age_group stays empty and age_as_of records the supplied date or the meet start-date compatibility fallback. No club competition categories are guessed. Unknown DOBs or ages outside configured bands have an empty group. Export generation/storage failures return 503 / EXPORT_FAILED and can be retried.

Dates use ISO `YYYY-MM-DD`; recording times use UTC ISO instants. `result` preserves the recorded display text, while `score` contains the normalized decimal value without locale separators or scientific notation. Unit/status/ranking direction use the existing enum names. Nulls become empty cells. Internal athlete/event/performance notes, communication notes, client retry IDs and athlete access secrets are omitted.

Rows are ordered by event date, event ID, case-insensitive athlete last/first name and athlete ID. Registration then orders by participation ID. Results order each athlete's attempts by known recording time (unknown last), then result ID. Records are fetched with their required context together, without loading every athlete's history or issuing per-row queries. Each request uses one read transaction; exporting does not change assignments, availability versions or result records.

Successful downloads use `text/csv;charset=UTF-8`, a UTF-8 BOM for spreadsheet Unicode detection, comma separators, RFC 4180 quoting and CRLF record endings. Commas, quotes and embedded line breaks round-trip as cell contents. Formula-like user text beginning with `=`, `+`, `-` or `@` after leading whitespace/control characters is prefixed with an apostrophe; leading tab/newline text is protected too. That prefix exists only in the download; stored data is unchanged. Numeric score/ID columns remain numeric. Machine readers should skip the initial BOM and account for the literal-text prefix in protected text cells.

Files are attachments named `meet-{id}-{meet-slug}-registration.csv` or `meet-{id}-{meet-slug}-results.csv`. Responses use `Cache-Control: no-store, private`, and configured allowed browser origins can read Content-Disposition through CORS. The meet name is converted to a bounded ASCII slug; the numeric meet ID and export type identify the file without allowing header injection.

| Situation | HTTP response |
| --- | --- |
| No assignments for REGISTRATION | 409 JSON with code NO_REGISTRATION_RECORDS and an explanatory message |
| No recorded results for RESULTS | 409 JSON with code NO_RESULT_RECORDS and an explanatory message |
| Unknown meet | 404 / MEET_NOT_FOUND |
| Nonpositive/malformed meet ID, missing or unknown type | 400 / INVALID_REQUEST |
| Anonymous / portal or unsupported role | 401 / 403 |

Type values are case sensitive: REGISTRATION or RESULTS. Empty exports return a message instead of a header-only file, and errors remain JSON even when a caller requests CSV. GET downloads need the staff session cookie in secure mode and do not require a CSRF write token. CSVs are generated synchronously in memory for the selected meet; this feature adds no external storage or background service.

Implementation: [MeetExportController](src/main/java/com/flyingangels/backend/meet/MeetExportController.java), [MeetExportService](src/main/java/com/flyingangels/backend/meet/MeetExportService.java), [MeetExportType](src/main/java/com/flyingangels/backend/meet/MeetExportType.java) and [MeetExportOptions](src/main/java/com/flyingangels/backend/meet/dto/MeetExportOptions.java).

### Event definitions

```json
{
  "name": "Long Jump",
  "rankingOrder": "DESC",
  "unit": "METRES"
}
```

`rankingOrder` is `ASC` or `DESC`. Unit is `SECONDS`, `METRES`, or `POINTS`. An omitted unit is derived from the name/ranking direction. Definition creation rejects an existing name; resolution by name reuses an existing definition. An existing category's measurement unit is not overwritten when it is reused.

### Meet-event creation

```json
{
  "name": "Spring Meet Long Jump",
  "eventDate": "2026-05-17",
  "location": "Example Stadium",
  "eventType": "Field",
  "externalUrl": null,
  "rankingOrder": "DESC",
  "meetId": 1,
  "categoryId": 2,
  "categoryName": null
}
```

The service resolves a category by ID, or by name when no ID is supplied. A new name can create a definition. Event date must fall within the meet. Ranking direction is also stored on the meet event and is the value used by its leaderboard; keep it consistent with the definition unless an override is intentional.

The database currently permits only one event of a given category per meet. Heats, rounds, divisions and equipment specifications are not part of that identity. An event that already has results cannot move to another meet or category through update.

### Bulk add and selected copy

Bulk creation wraps existing event requests in `{"events": [...]}` and requires all objects to reference the path's target meet. The batch is transactional.

Copy takes `{"sourceMeetId": 1, "eventIds": [2, 3]}`. IDs must be distinct and belong to the source meet. Each copy:

- Reuses its category and copies name, event type, URL and ranking direction.
- Uses the target meet's start date and location.
- Starts with no participation or results.
- Must pass the target meet/category uniqueness rule.

## 8. Participation and results

### Independent assignments

Create one assignment with `{"athleteId": 7}` or many with `{"athleteIds": [7, 8]}`. Duplicate assignments produce a conflict; duplicate IDs inside the batch are invalid input. An athlete can enter multiple different events.

`EventParticipationResponse` includes `participationId`, athlete identity, event ID/name, meet ID/name, and category ID/name. Participant retrieval sorts by athlete last name, then first name.

### Append versus correction

Create a performance using:

```json
{
  "athleteId": 7,
  "result": "2:44.07",
  "resultLabel": "Final",
  "unit": "SECONDS"
}
```

`POST /api/events/{id}/results` appends a record and creates participation if necessary. Repeating a POST without a clientId creates another attempt. An optional clientId makes retries return the original record for the same participation; use a result-ID PUT to correct its value or label.

`PUT /api/events/{id}/results/{resultId}` corrects one record. Its request can also change the athlete; the service creates participation for the replacement athlete if needed. The original athlete's assignment is not automatically removed.

The compatibility route `PUT /api/events/{id}/participants/{athleteId}/result` requires an existing assignment and **appends** a result. Its behavior is not idempotent despite using PUT; new clients should use POST results for new performances.

The result-batch wrapper is `{"results": [...]}`. One invalid record rolls back the complete batch. Bulk POST creates attempts. New `PUT /api/events/{id}/results/bulk` corrects existing attempts using `resultId`, `result`, optional `score` and `unit`; it preserves athlete/event ownership, IDs and recording timestamps. See [bulk editing and Results Overview](../docs/sprint2-api-updates.md).

### Result labels

Create, individual update, bulk create/update, participant-result append and browser-data migration requests accept optional `resultLabel` text, for example `Heat 1`, `Final`, or `Attempt 2`. Labels are trimmed and limited to 100 characters. They are descriptive labels, not new heat/round entities or unique identifiers; identical performances can have separate labeled result IDs.

For corrections, an omitted or null resultLabel preserves the stored label so existing clients do not erase it. An explicit empty/whitespace label clears it. Labels are returned by event results/profiles/leaderboards, athlete profiles/history, Results Overview and the restricted own-athlete portal projection. New nullable database storage preserves all existing unlabeled rows; no database reset is required. Validation failure in a batch rolls back score and label changes together.

### Numeric measurement rules

`result` retains the trimmed source text. `score` is the normalized comparable number. A supplied numeric score must equal the parsed value. A supplied unit must match the category's unit.

| Input | Unit | Stored numeric score |
| --- | --- | --- |
| `12.40` | SECONDS | 12.40 |
| `2:44.07` | SECONDS | 164.07 |
| `02:44.1` | SECONDS | 164.1 |
| `5.18m` | METRES | 5.18 |
| `4.92` | METRES | 4.92 |
| `6000` | POINTS | 6000 |

Colon-form track times support minutes and seconds, with seconds from 00 to 59 and an optional fractional part. Hours-style timestamps, decimal-comma values, qualifier suffixes and malformed values are not normalized automatically. Numeric values allow at most eight integer digits and four decimal places. No event-specific plausibility thresholds are enforced.

### Result statuses and provenance

| Status | Meaning in the current backend |
| --- | --- |
| VALID | Parseable numeric performance; does not mean officially verified |
| NT | No time |
| DNS | Did not start |
| DNF | Did not finish |
| DQ | Disqualified |
| FOUL | Foul attempt |
| PENDING | Compatibility/model state for an unrecorded value; new assignments use participation instead |
| UNPARSED | Preserved historical input that cannot be normalized |

Non-performance inputs such as `NT` require a null/omitted numeric score. `PENDING` and `UNPARSED` are not accepted as manual performance inputs. Manual unrecognized formats return a validation error; historical import keeps them as `UNPARSED` with a null score.

Results have `recordedAt` and `source`. New manual results use source `MANUAL`; historical imports use `LEGACY_IMPORT`. Recording time is insertion time, not necessarily competition time. Migrated rows may have unknown/null recording times. There is no author, verifier, official-result flag, or row-level import-job foreign key on `EventResult`.

### Results Overview

`GET /api/results` provides paginated competition results across meets, with athlete search, meet/event/category/gender filters, inclusive age bounds at each meet's start date, and date/athlete/event-appropriate performance ordering. It returns every matching attempt; event-less historical imports remain in athlete history. See [query parameters and response](../docs/sprint2-api-updates.md).

### Leaderboard algorithm

The event service:

1. Considers results attached to that event.
2. Keeps only records with a numeric score and `VALID` status.
3. Selects each athlete's best attempt according to the event's `ASC`/`DESC` direction.
4. Sorts best attempts by score, with ascending case-insensitive athlete names for ties.

The response is a list of result DTOs, not a separately stored ranking table. Historical event-less imports are not included. No season, age group, indoor/outdoor, wind, official-verification or ranking-point eligibility is evaluated.

### Athlete competition history, personal bests and progression

`GET /api/athletes/{id}/history` consolidates one athlete's meet assignments and recorded competition attempts. Optional `categoryId` filters by reusable event definition across meets, rather than by a single meet-event ID. The endpoint is read-only and available to Admin and Coach staff sessions; anonymous callers receive 401 and portal/unsupported roles receive 403. Athlete/parent access remains on its separately scoped portal API.

| Response field | Meaning |
| --- | --- |
| athlete | Athlete ID and first/last name |
| selectedCategoryId | Supplied filter, otherwise null |
| status | AVAILABLE, NO_COMPETITION_HISTORY, or NO_MATCHING_EVENTS |
| availableCategories | All competition definitions associated with this athlete; retained when a filter has no matches |
| meets | Chronological meets with dates/location and nested events, participation IDs and every recorded attempt |
| performances | Comparable per-definition/unit/ranking-order summaries: attempt counts, personal best and progression points |
| unlinkedResults | Imported or legacy performances without known meet/event context, kept separate from competition summaries |

Meets are ordered by start date then ID; events by event date then ID; attempts within an event by recording time then result ID, with unknown recording times last. Events without results remain present with status NO_RESULTS and an empty attempts array. Existing linked results without a participation row remain visible with a null participationId; reading history never synthesizes an assignment or claims attendance.

Personal bests use VALID results with nonnegative normalized scores, a dated linked event and matching category/unit. Non-performance statuses (NT, DNS, DNF, DQ, FOUL, PENDING, UNPARSED), null scores and incompatible units remain visible but do not enter PB/chart calculations. Comparison follows each event's configured rankingOrder: ASC selects the lowest score; DESC selects the highest. Different definitions, units or order overrides never share a comparison series. These are best recorded competition values, not certified official records; wind, age-season and official-verification rules are not invented.

Each progression series contains the best valid attempt per competition event, ordered by competition eventDate rather than the time someone entered the result. Every attempt remains in meets[].events[].attempts[]. A point carries result/meet/event IDs, competition date, raw result, label, normalized score and recording time. Tied best values choose the earliest competition date, then recording time/result ID, for a stable representative.

| progressionStatus | Backend chart-data state |
| --- | --- |
| NO_VALID_PERFORMANCE | personalBest is null and progression is empty |
| SINGLE_COMPETITION | One competition point; no multi-point progression is implied, even if that competition has several attempts |
| PROGRESSION_AVAILABLE | At least two competition points, ordered chronologically |

Imported historical values do not receive an invented meet/date or compete with linked results for a PB. Legacy values already represented by central unlinked results are not duplicated in unlinkedResults. Category filtering applies to competition events, summaries and matching unlinked values. A known category with no matching events returns 200/NO_MATCHING_EVENTS; malformed/nonpositive filter IDs return 400; missing athletes/categories return 404. Edits or deletions through the existing result APIs are reflected immediately because summaries derive from the same canonical result records.

Implementation: [AthleteHistoryController](src/main/java/com/flyingangels/backend/athlete/AthleteHistoryController.java), [AthleteHistoryService](src/main/java/com/flyingangels/backend/athlete/AthleteHistoryService.java), [AthleteHistoryResponse](src/main/java/com/flyingangels/backend/athlete/dto/AthleteHistoryResponse.java) and [PerformanceSummaryService](src/main/java/com/flyingangels/backend/event/PerformanceSummaryService.java). No frontend code or chart renderer is included.

### Centralized rankings

Configure club age bands with APP_AGE_GROUP_BANDS, using nonoverlapping inclusive completed-year ranges, for example `Youth:0:17;Adult:18:150` (illustration only, not a prescribed club policy). Empty configuration disables named categories. GET `/api/age-groups` and scoped GET `/api/portal/age-groups` return configured labels, bounds and the age calculation rule. Select `ageGroup=Youth&ageAsOf=2026-11-12`, or retain numeric minAge/maxAge; supplying both is rejected. When bands are configured, all ranking requests require ageAsOf, even without an age filter, so the staff table's ageGroup/ageAsOf fields have an explicit reference. Missing/future DOBs remain unclassified. Season filtering is deliberately disabled: a nonblank season parameter returns 400 until reliable season attribution is agreed.

Rankings compare each athlete's best recorded competition performance for one reusable event definition across meets. The APIs are read-only and compute results from the same canonical records used by athlete history and Meet Details.

| Endpoint | Access and response |
| --- | --- |
| GET `/api/rankings/categories` | Admin/Coach: available event definitions with ID, name, unit and ranking direction |
| GET `/api/rankings/meets` | Admin/Coach: ranking meet choices with ID/name/start/end dates |
| GET `/api/rankings/events?meetId=...` | Admin/Coach: meet-event choices; omit meetId to list all event instances |
| GET `/api/rankings` | Admin/Coach: best result per athlete with result/meet/event context and authorized profile access |
| GET `/api/portal/rankings/categories` | Activated Athlete account: all event definitions |
| GET `/api/portal/rankings/meets` | Athlete ranking choices across all past/current/future meets, including meets without a nomination |
| GET `/api/portal/rankings/events?meetId=...` | Athlete meet-event choices for any meet; omit meetId to list all event instances |
| GET `/api/portal/rankings` | Rankings for any category/meet/event, with performance/meet/event context and own-profile access only |

Both ranking endpoints accept:

| Parameter | Contract |
| --- | --- |
| categoryId | Required positive reusable event-definition ID; unknown ID returns 404 |
| meetId | Optional positive meet ID; select any meet, including one without the authenticated athlete's nomination; unknown ID returns 404 |
| eventId | Optional positive meet-event ID; must match categoryId and, when supplied, meetId; selecting an event also identifies its meet |
| gender | Optional case-insensitive filter; recognized Male/Men and Female/Women aliases normalize to stored canonical values; blank clears it |
| minAge, maxAge | Optional inclusive completed-year bounds, 0..150, min <= max |
| ageAsOf | Explicit ISO date required when an age bound/group is supplied or club bands are configured; otherwise optional |
| page, size | Zero-based page (default 0), size 1..100 (default 25) |

Example: `GET /api/rankings?categoryId=3&gender=Female&minAge=12&maxAge=14&ageAsOf=2026-05-17&page=0&size=25`. Age is computed on that one date for every athlete, rather than on different competition dates. A February 29 birth advances on March 1 in non-leap years. Unknown/not-yet-born DOBs are excluded only with an active age filter. Age-group labels are mapped to numeric bounds by callers; the backend does not invent athletics categories. Competition results from all dates remain eligible; ageAsOf is an age reference, not a competition-date cutoff.

`RankingsResponse` returns category metadata, applied filters, status, content, page, size, totalElements and totalPages. The database selects one best eligible attempt per athlete before paging; the service assigns positions across that complete best-athlete set and then slices the requested page. Repeated attempts cannot consume ranking slots. Equal numeric scores share competition positions such as **1, 1, 3**, including when a tie crosses page boundaries. Case-insensitive last/first names and athlete ID stabilize display order without breaking score ties. A page beyond the final page returns empty content with the real totals and AVAILABLE status.

Eligibility uses the shared CompetitionResultPolicy: a dated linked event, matching category/unit, VALID status, and a nonnegative normalized score. Imports without a known meet/event, null/unparsed/non-performance values, and incompatible units are excluded. Across all meets, rankings use the selected definition's configured order and exclude opposite-order event overrides. Selecting a meet or event uses that event's effective configured order, so every configured event can be ranked consistently with its own rules. The meet/event scope is applied to candidate results and best-result selection before ranking and pagination. A better performance outside the selected meet cannot remove or replace the athlete's selected-meet performance. Archived athletes' recorded performances remain in historical rankings; the staff projection identifies archived rows.

Equal best attempts for one athlete select the earliest competition date, then known recording time (unknown last), then result ID, matching history's stable PB representative for that comparison configuration. Updating or deleting a result immediately changes its derived ranking. The compound event-results ranking index supports category/athlete/status/unit/score lookups; no rank table, performance copy, new background job or external service is introduced. These are recorded competition rankings; season, wind and official-verification policies remain separate club decisions.

Staff rows include position, athlete ID/name/gender, ageAtReference when a date is supplied, archive state, result ID/label/raw value/score, meet/event/date context and profileAccessible=true. Athlete ranking rows include position, athlete name, raw performance/score/unit, associated meet ID/name, event ID/name/date, and ageGroup/ageAsOf where available. Athletes can compare every category and meet even when they have no assignments or results there; distinct disciplines remain separate ranking tables. Their athleteId is null and profileAccessible=false for other athletes; only the authenticated athlete receives their own ID and true. DOB, staff notes, other athletes' IDs and internal result IDs are excluded. The ranking selectors return competition metadata only, never nominee lists, staff notes or availability details. My Meets remains restricted to nominations, and private profile/history access remains restricted to the authenticated athlete. Staff API endpoints remain forbidden to Athlete accounts. Expired or invalidated account sessions lose ranking access on their next request.

Example Athlete workflow: GET `/api/portal/rankings/meets`, GET `/api/portal/rankings/events?meetId=12`, then GET `/api/portal/rankings?categoryId=3&meetId=12&eventId=24&ageAsOf=2026-11-12`. Omit meetId/eventId to compare best performances for that category across all meets. Known meets/categories without comparable results return the normal empty state; incompatible category/meet/event selections return 400.

| Status | Meaning |
| --- | --- |
| AVAILABLE | At least one eligible athlete matches; requested page can be empty when out of range |
| NO_EVENT_RESULTS | Selected definition has no comparable rankable competition results |
| NO_MATCHING_ATHLETES | Comparable results exist, but the selected gender/age filters match nobody |

Implementation: [RankingsService](src/main/java/com/flyingangels/backend/ranking/RankingsService.java), [RankingQueryRepository](src/main/java/com/flyingangels/backend/ranking/RankingQueryRepository.java), [RankingsResponse](src/main/java/com/flyingangels/backend/ranking/dto/RankingsResponse.java) and [CompetitionResultPolicy](src/main/java/com/flyingangels/backend/event/CompetitionResultPolicy.java). No frontend changes are included.

### Athlete portal and meet availability

Athlete accounts use GET `/api/portal/profile` for their basic profile, GET `/api/portal/history?categoryId=...` for their own history/personal bests/progression, and GET `/api/portal/meets?view=UPCOMING|PAST|ALL` for nominated meets. GET `/api/portal/meets/{meetId}` returns one nomination and rejects unrelated meet IDs. These endpoints derive the athlete from the current login, not from caller-supplied athlete IDs. They never include staff coaching notes or allow result edits.

Every athlete-event assignment creates or updates **one availability request per athlete per meet**. Assigning two events in the same meet produces one request containing both events. Explicit/bulk assignment, result entry that implicitly assigns an athlete, and browser-data migration all use this rule in the same transaction. Failed assignments roll back availability changes too. Existing assignments are backfilled idempotently at application startup.

| Endpoint | Access and response |
| --- | --- |
| GET `/api/meets/{id}` | Admin/Coach: existing meet/events plus `availabilityRequests` |
| GET `/api/meets/{id}/availability` | Admin/Coach: active meet requests with athlete identity, assigned events and response status; empty array when nobody is assigned |
| GET `/api/availability/{id}` | Admin/Coach: one request, including inactive requests retained for audit |
| GET `/api/availability/{id}/responses` | Admin/Coach: confirmation history, newest first, with source, staff user ID, method, note, timestamp and covered assignment revision |
| PUT `/api/availability/{id}/response` | Admin/Coach: record a response received externally |
| GET `/api/portal/profile` | Scoped athlete session: existing profile/previous results, assignment meet/event IDs and upcoming `availabilityRequests` |
| GET `/api/portal/availability` | Scoped athlete session: own active upcoming/current meet requests, ordered by meet start date and request ID |
| GET `/api/portal/availability/{id}` | Scoped athlete session: own active request details |
| PUT `/api/portal/availability/{id}/response` | Scoped athlete session: confirm or change own availability |

Activated Athlete accounts sign in through `POST /api/auth/login` and receive ROLE_ATHLETE. Passwordless profile-link login is not supported. The server derives athlete scope from the validated session; supplied athlete IDs cannot expand it. Other athletes' request IDs return 404. Portal sessions cannot call staff availability/audit endpoints. Request/profile responses omit staff notes, staff contact details, token identifiers and token secrets. Expired or invalidated account sessions lose portal access. Secure mode requires CSRF tokens for all confirmations; refresh the token after switching identities.

Read the request before replying and copy its `version` and `assignmentRevision`. A portal reply is:

```json
{
  "status": "AVAILABLE",
  "version": 0,
  "assignmentRevision": 1
}
```

A staff-recorded external reply adds a required communication method and optional internal note:

```json
{
  "status": "UNAVAILABLE",
  "version": 0,
  "assignmentRevision": 1,
  "communicationMethod": "PHONE",
  "note": "Parent confirmed by phone"
}
```

`status` accepts only AVAILABLE or UNAVAILABLE. External methods are PHONE, WHATSAPP, EMAIL, TEXT, IN_PERSON and OTHER; PORTAL is assigned by the server to portal confirmations and is rejected on the staff endpoint. Notes have a 1,000-character limit and are available only in staff audit/overview projections. Staff author IDs come from their authenticated session; local authentication-bypass runs leave the author ID null.

New requests have PENDING status. Adding or removing an event increments `assignmentRevision` and returns the effective status to PENDING. The previous answer, covered revision and timestamp remain visible as `lastAnswer`, `answeredRevision` and `respondedAt`; those fields do not imply current confirmation. Replying after reviewing the current events establishes fresh coverage. Changed confirmations append audit entries. An identical retry of the last submission, from the same identity/channel and with the same original version, revision, answer and remarks, returns the existing response without a new audit entry. A conflicting stale version or assignment revision returns **409**, requiring a reload; concurrent conflicting replies cannot silently overwrite one another. Successful replies return the updated version.

Removing the final assignment hides the inactive request from active lists and closes replies, preserving its audit history. Reassigning the athlete to that meet reuses the request ID and requires a new response. Deleting an event updates affected requests; an assigned event must have its assignments removed before moving to another meet. Physical athlete/meet deletion removes the dependent requests and response history with the rest of that record's data.

Availability closes at the meet's start date and optional startTime, with midnight as the default, in `APP_AVAILABILITY_TIME_ZONE` (default `America/Toronto`). Submissions at or after responseDeadline return 409; no existing answer changes. In-progress meets remain in Upcoming until the end date but have canRespond=false. Request details expose responseDeadline and timeZone. Adding/removing events or changing event date/definition/location or meet dates/start time/location requires reconfirmation. Failed saves return 503 and roll back to the last confirmed state. A meet lock, unique meet/athlete constraint and version checks serialize changes.

Implementation: [AvailabilityService](src/main/java/com/flyingangels/backend/availability/AvailabilityService.java), [AvailabilityController](src/main/java/com/flyingangels/backend/availability/AvailabilityController.java), [PortalAvailabilityController](src/main/java/com/flyingangels/backend/availability/PortalAvailabilityController.java) and [AvailabilityDtos](src/main/java/com/flyingangels/backend/availability/AvailabilityDtos.java). See [meet views and centralized availability](#meet-views-and-centralized-availability) for staff overview/filtering APIs.

## 9. Athlete notes

Admin and Coach share all note endpoints. Both can create/read athlete notes and save/read event and participant notes. `MANAGE_NOTES` is returned in both roles' session permissions; Spring Security enforces role access. Athlete accounts cannot access staff notes, including their own athlete's staff notes. See the [complete endpoint role matrix and Postman instructions](../docs/postman/endpoint-role-access.md).

The general `Athlete.note` field remains separate from note history.

```json
{
  "text": "Worked on approach consistency",
  "eventId": 12
}
```

Omit `eventId` for an athlete-level historical note. The service verifies the athlete and optional event exist. It does not require the athlete to be assigned to that event. Text is required and limited to 2,000 characters.

`createdAt` is assigned by the server. Retrieval orders by timestamp descending, then ID descending. Responses include note/athlete IDs, text, creation time, and optional event/meet names and IDs.

Only create/list endpoints currently exist. Note editing, individual note deletion, note authorship and audit history are not implemented. Removing an event preserves the text but removes its event context; it does not snapshot the deleted meet/event names.

## 10. Spreadsheet import design

For the practical request sequence and real-file troubleshooting, see [preview behavior](../docs/backend-workflows.md#3-what-preview-actually-does), [commit behavior](../docs/backend-workflows.md#4-what-commit-actually-does) and the [Postman client-file walkthrough](../docs/backend-workflows.md#6-postman-workflow-for-the-real-client-file).

### One feature module and one import service

All import code lives in `importing/`. `athlete/` owns ordinary profile CRUD and entity behavior; it no longer contains an import service or import HTTP route. `AthleteImportController` exposes every upload operation, including the existing immediate endpoint.

The import module has separate collaborators because they perform different jobs:

| Component | Responsibility |
| --- | --- |
| `AthleteImportService` | Single coordinator for preview, retrieval, commit and immediate upload |
| `AthleteRegistrationParser` | CSV/TSV/XLSX reading, column mapping, validation and source-value warnings |
| `AthleteImportMatcher` | Resolve an existing athlete; determine NEW, UPDATE, UNCHANGED, INVALID or AMBIGUOUS |
| `ImportRowWriter` | Apply one create/update transaction; preserve blanks and historical results; persist outcome |
| `ImportJob` / `ImportRow` and repositories | Store original uploads, source-row outcomes and commit locks |
| Import request/response records | Define selection and summary contracts |

These are not competing import implementations. Both HTTP workflows use the same parser, matcher and row writer. `Athlete.mergeImported()` is an entity mutation helper; it does not read files or orchestrate imports.

### Create, update, or skip

| Incoming record | Behavior |
| --- | --- |
| New or existing athlete, no DOB provided in the upload | INVALID; do not create or update the athlete |
| Existing athlete, registered identifier confirms identity and later DOB supplied | Update the existing athlete rather than create a second record |
| Existing athlete, changed nonblank details | Apply supplied changes and report UPDATED |
| Existing athlete, blank optional cells and valid DOB | Preserve previously stored optional values |
| Existing athlete, new performance values | Append previously unseen values; retain earlier performances |
| Existing athlete, no supplied changes | UNCHANGED; skip writing that row |
| Existing name/DOB match without an identifier, or several unresolved exact matches | AMBIGUOUS; block until registration identity is supplied/resolved |
| Same name, known different DOB, no registered-ID match | NEW; create a different athlete without changing the existing person |
| Unusable, today or future DOB | INVALID; do not apply the row, even if the matched athlete has a stored DOB |
| Other invalid required field | INVALID; explain the problem without blocking other valid rows |

An existing athlete is not rejected merely because they already exist. Only unchanged rows are skipped as no-ops. Invalid rows and genuinely ambiguous identity matches are separately explained.

Import updates require a valid incoming DOB. Blank optional gender, status or general note cells preserve existing information. Nonblank supplied notes replace the general note; separate timestamped note history remains untouched. Invalid DOB rows never modify a matched athlete. Clearing a stored optional value must be an explicit profile-edit operation, not an empty spreadsheet cell. This required-DOB policy supersedes the earlier permissive import rule; see [ADR-0008](../docs/adr/0008-required-import-birth-dates.md).

### Parser behavior and headers

The parser reads the first XLSX worksheet for `.xlsx` filenames. Other uploads are UTF-8 text: a tab in the header selects TSV, otherwise comma CSV. It handles the source BOM and the club's embedded quotes around the FLYA heading.

It requires first/last name headers, supports reordered columns and blank rows/spacer headings, and rejects duplicate nonblank normalized headings or extra populated values beyond the header. Limits are 10 MB and 10,000 input rows. `New People:` alone is not a valid header.

| Value | Accepted heading examples |
| --- | --- |
| firstName | FIRST Name / First Name |
| lastName | LAST NAME / Last Name |
| note | NOTE / Notes |
| flyStatus | FLY / Fly Status / (FLY) Flying Angels TF Academy |
| flyaStatus | FLYA / Flya Status / "FLYA" Flying Angels Academy |
| dateOfBirth | DATE OF BIRTH / DOB |
| gender | Gender / Sex |
| externalId | MEMBER # / Member Number / Member ID / Registration ID |

Recognized performance columns include numeric metre-distance races and relays, hurdles, miles, steeplechase, jumps, vaults, throws and point/combined events. This covers all 31 event columns in the supplied club roster. Unknown administrative headings remain in the original file rather than being treated as results.

Every imported row requires a valid past DOB, supplied as an ISO date or typed XLSX date cell. Missing DOB, unusable values such as `0000-00-00`, `40700` or `No birth date`, and dates today/in the future make the row INVALID. Other valid rows remain selectable. Missing gender is allowed with a warning; unknown gender text is retained with a warning. Malformed or qualifier-suffixed results remain historical UNPARSED values with a null comparison score; results over 50 characters are invalid.

Event-column recognition and default measurement units share `ResultValues`: races, hurdles, miles and steeplechase use SECONDS; jumps, vaults and throws use METRES; recognized combined-event/point definitions use POINTS. Imported definitions default to ASC for time and DESC for distance/points. A race value `2:44.07` normalizes to 164.07 seconds; a jump/throw value `5.18m` normalizes to 5.18 metres. Stored athlete-profile results include raw result, score, unit and parsing status. Values incompatible with their event unit are retained as UNPARSED with an expected-unit warning, rather than being treated as comparable scores.

For XLSX, calendar-date conversion applies only to the DOB column. Event cells retain their displayed formatting, including Excel duration cells such as `2:44.07` and `5:43.80`; they must not become the Excel base date `1899-12-31`. CSV/TSV values are read as text. If an exported CSV already contains a base-date string instead of the performance, the time cannot be reconstructed from that string: correct the export or upload the original workbook and create a new preview. Re-uploading does not automatically delete earlier malformed historical values that were already committed.

### Matching an athlete

1. Validate the required incoming DOB, then match an exact `(source, externalId)` pair where supplied. Confirmed identifier matches support name/DOB corrections.
2. Without an identifier match, compare first/last names ignoring case and compare DOB exactly. A known different DOB represents a different person, even when there is only one existing name match: NEW.
3. If a name/DOB match exists but the upload has no identifier, return AMBIGUOUS and block saving. This applies even to one exact match and identical incoming information; gender does not bypass review.
4. A supplied identifier can attach to one eligible unkeyed exact name/DOB match. Several exact candidates require resolution. An explicitly different existing identifier represents a distinct identity.
5. A same-name legacy record with unknown DOB requires its registered identity; do not update by name alone or guess that it is a new person.

Source defaults to `UPLOAD`, is trimmed/uppercased, and is limited to 80 characters. Keep it consistent for that registration dataset. To resolve a blocked unkeyed match, obtain the actual registration identifier, add it to the source and create a new preview. Do not fabricate an identifier. This matching policy is recorded in [ADR-0009](../docs/adr/0009-name-dob-and-confirmed-identifiers.md).

Rows are processed in original source order and eligible rows are rechecked against earlier saved rows. Confirmed identifiers allow later supplied information to update earlier values, append new performances and skip unchanged repetitions. Identical parsed registrations repeated within the same file are marked DUPLICATE during preview and skipped. Other repeated identities carry `repeatedOfRowNumber` and an explanation while retaining their normal classification. Non-identical unkeyed name/DOB rows can still become AMBIGUOUS after an earlier row creates the athlete. A previewed NEW row can consequently become IMPORTED, UPDATED, UNCHANGED or AMBIGUOUS during commit.

### Workflow and persistence

```text
Upload -> parse -> match and compare -> store original file and preview rows
                                                  |
                                                  v
                                      Select NEW / UPDATE source rows
                                                  |
                                                  v
                             Recheck identity and differences for each row
                                                  |
                                      create / update / skip unchanged
                                                  |
                                                  v
                              imported / updated / skipped / failed summary
```

Preview response rows contain `rowNumber`, `status`, `explanation`, matched/imported `athleteId`, parsed `athlete`, and optional `externalId`. An UPDATE explanation names changed fields. Warnings are included in the explanation, which is stored with a 2,000-character limit.

Original bytes are stored in `ImportJob`; classifications/outcomes are stored in `ImportRow`. Retrieval reparses the file under the current parser implementation, not a frozen parsed DTO snapshot. Text row numbers are parsed record numbers plus the header offset; XLSX uses worksheet row numbers. There is no original-file download, expiry or automatic retention cleanup endpoint.

During commit:

1. Reject empty/repeated selections or row numbers outside the job.
2. Process selected NEW/UPDATE rows (and legacy stored VALID previews).
3. Lock the preview row and recheck current athlete identity and changes.
4. Lock/refresh an existing matched profile before applying its changes.
5. Save the profile and any historical performance additions in one `REQUIRES_NEW` transaction.
6. Record IMPORTED, UPDATED or UNCHANGED. A caught failure rolls back that row and records FAILED separately.

There is no outer batch transaction: successful rows remain saved when another row fails. The writer is a separate bean to ensure Spring applies its transaction boundary. Row locks prevent the same preview row from applying twice; stable external-ID uniqueness is still needed to protect every cross-job identity race.

### Statuses and summary counts

| Status | Meaning |
| --- | --- |
| NEW | No existing athlete matched; ready to create |
| UPDATE | Existing athlete has supplied changes; ready to update |
| UNCHANGED | Existing information and supplied performance values have not changed; skip |
| DUPLICATE | Identical registration already appears in this upload; skipped with a source-row reference |
| INVALID | Row validation failed |
| AMBIGUOUS | Multiple possible identities require review |
| IMPORTED | New athlete successfully created |
| UPDATED | Existing athlete successfully updated |
| FAILED | Row save failed and rolled back |
| SKIPPED | Response-only label for an excluded eligible row |

Summary fields are `imported`, `updated`, `skipped` and `failed`. Imported/updated counts are cumulative for the job. Skipped includes unchanged and excluded rows; selected INVALID/AMBIGUOUS/FAILED rows contribute to failed. Totals cover all source rows.

Excluded eligible rows remain selectable. Retrying an IMPORTED or UPDATED preview row does not reapply it. Create a new preview to apply a newer registration file or retry corrected failed data. UNCHANGED classification is a no-op, not an API error.

### Immediate endpoint and compatibility

`POST /api/athletes/import` now creates a preview and commits all its rows through the same service. It accepts optional `source`, uses the same create/update/no-change rules, and has the same per-row rollback behavior as selected commit.

Its response retains `athleteCount` (new athletes), `eventCount` (incoming event entries on newly created rows) and `duplicateCount` (unchanged rows), and adds `updatedCount`, `skippedCount` and `failedCount`. Updated athletes are not counted as rejected duplicates. This endpoint no longer has a separate whole-file legacy transaction or fingerprint-based matching service.

### Performance history

Historical spreadsheet values do not acquire an invented meet link. New event/value pairs are appended; existing performances are not overwritten or deleted. Matching against both legacy values and central results avoids reinserting the same supplied value. Value comparison uses case-insensitive category names and trimmed raw result text; differently formatted but numerically equal source text can still represent a new value.

The historical store cannot infer whether two identical raw values came from different competitions without source competition identity. Official-result verification and personal-best/season-ranking eligibility remain separate club decisions.

## 11. Complete API reference

Base URL in local development: `http://localhost:8080`. Paths below are relative to it. JSON is the default request/response format except multipart uploads. DELETE successes return no body.

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
| PUT | `/api/events/{id}/results/bulk` | `{results: [{resultId, result, score?, unit?}]}` | 200; atomic correction array |
| PUT | `/api/events/{id}/results/{resultId}` | Result request | 200; corrected result |
| DELETE | `/api/events/{id}/results/{resultId}` | Both IDs | 204 |
| PUT | `/api/events/{id}/participants/{athleteId}/result` | `{result, score?, unit?}` | 200; newly appended result |
| GET | `/api/events/{id}/leaderboard` | Event ID | 200; best result per athlete |
| GET | `/api/results` | Optional athlete search, meet/event/category/gender/age filters, sort and paging | 200; paged Results Overview |

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

### Result and import response examples

A representative result response is:

```json
{
  "resultId": 101,
  "athleteId": 7,
  "firstName": "Example",
  "lastName": "Athlete",
  "categoryId": 2,
  "categoryName": "800m",
  "result": "2:44.07",
  "score": 164.07,
  "eventId": 12,
  "eventName": "Spring Meet 800m",
  "meetId": 1,
  "meetName": "Spring Meet",
  "unit": "SECONDS",
  "status": "VALID",
  "recordedAt": "2026-10-14T16:00:00Z",
  "source": "MANUAL"
}
```

Historical responses can have null event/meet context, score or recorded time. A legacy-only value also has null result/category IDs and unit. Clients must handle these explicitly.

Commit response shape:

```json
{
  "importId": "example-job-uuid",
  "imported": 1,
  "updated": 0,
  "skipped": 2,
  "failed": 0,
  "rows": []
}
```

The empty `rows` above only illustrates the envelope; real responses include all source rows and their outcomes. Create a preview before obtaining a real job ID.

### Local development walkthrough

Create an athlete:

```sh
curl -X POST http://localhost:8080/api/athletes \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Example","lastName":"Athlete","gender":"Female","events":[]}'
```

Create a meet and definition, then use their returned IDs to create an event and record results using the payloads above. IDs are database-generated; do not assume they start at 1 in an existing environment.

Preview a source file, then select its row numbers:

```sh
curl -X POST http://localhost:8080/api/athletes/import/preview \
  -F 'file=@/path/to/athletes.csv' \
  -F 'source=CLUB_ROSTER'

curl -X POST http://localhost:8080/api/athletes/import/REPLACE_WITH_IMPORT_ID/commit \
  -H 'Content-Type: application/json' \
  -d '{"rowNumbers":[2,4]}'
```

These commands assume anonymous local development mode. Secure mode requires authentication and write-request CSRF handling.

## 12. Validation and error handling

Controllers use `@Valid`; DTOs apply required, size, date, positive-ID, URL and nonnegative-score constraints. Services add existence, ownership, uniqueness, date-range, unit and normalized-score checks. Database constraints remain the final enforcement for unique source IDs, assignments and meet/category pairs.

| Request area | Main limits |
| --- | --- |
| Athlete names | Required, at most 100 characters each |
| General athlete note | At most 500 characters |
| Team statuses | At most 100 characters each |
| Gender | At most 30 characters |
| Meet/event/category names | Required, at most 150 characters |
| Location | At most 150 characters |
| Event type | At most 100 characters |
| External URL | HTTP/HTTPS when provided, at most 500 characters; use null for absent URL |
| Result text | Required, at most 50 characters |
| Note-history text | Required, at most 2,000 characters |
| Event/copy batches | 1–200 entries |
| Participation/result batches | 1–500 entries |
| Commit selection | Non-null, at most 10,000 row numbers; an empty selection is allowed |
| Multipart upload | 10 MB file limit, 11 MB request limit |

Typical application error envelope:

```json
{
  "timestamp": "2026-10-14T16:00:00Z",
  "status": 409,
  "code": "ATHLETE_ALREADY_ASSIGNED",
  "message": "Athlete 7 already assigned to event 12",
  "path": "/api/events/12/participants"
}
```

| HTTP status | Codes / situations |
| --- | --- |
| 400 | `INVALID_REQUEST`, `VALIDATION_ERROR`, `IMPORT_READ_FAILED` |
| 404 | `ATHLETE_NOT_FOUND`, `MEET_NOT_FOUND`, `EVENT_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `PARTICIPANT_NOT_FOUND`, `IMPORT_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `ATHLETE_ALREADY_EXISTS`, `ATHLETE_ALREADY_ASSIGNED`, `DATA_CONFLICT` |
| 500 | `INTERNAL_ERROR` with a generic client message |

A missing event result currently uses `EVENT_NOT_FOUND`, not a separate result-specific code. General database integrity failures use a generic DATA_CONFLICT message. Authentication/authorization errors are handled by Spring Security before MVC and need not share this envelope. Not every framework-level failure, including upload-limit failures, has a dedicated application handler.

## 13. Transactions, concurrency, and fetching

### Transaction boundaries

| Operation | Boundary |
| --- | --- |
| Athlete/profile changes | Service transaction; response assembled consistently with current data |
| Meet/event operations | Service transaction |
| Event creation/copy batches | Whole batch rolls back on failure |
| Participation/result batches | Whole batch rolls back on failure |
| Preview creation | One transaction for job and row records |
| Preview commit coordinator | No encompassing transaction |
| Import row write | Independent `REQUIRES_NEW` transaction |
| Failed row outcome | Separate `REQUIRES_NEW` transaction after rollback |
| Immediate import | Adapter over preview + commit; same independent row transactions |

`ImportRowWriter` is a separate Spring bean so its transaction annotation is invoked through Spring's proxy. Moving it into a self-invoked helper in the coordinator would change this behavior.

The row writer uses a pessimistic write lock to stop the same preview row being committed twice concurrently. Unique database keys protect assignment, meet/category and external-ID invariants during competing requests. Check-before-save logic alone is not a concurrency guarantee.

User accounts, assignments and availability requests have optimistic versions. Availability also recognizes identical retries of the last confirmation; result corrections still have no general request idempotency key. Demographic matching without a stable external ID is not database-unique and can still race between jobs.

### Fetching and DTO construction

`spring.jpa.open-in-view=false` disables persistence access throughout the web view/serialization phase. Repositories use entity graphs for required event/category/athlete/meet context; services assemble richer athlete and note responses while in transactions.

Event result and participation collections use sets so fetching both avoids the multiple-bag problem of simultaneously fetching two unindexed JPA lists. Do not rely on those sets to provide a stable chronological API order. Notes and participant lists have explicit ordering; result lists generally do not.

Large event/meet reads can fetch substantial related data. Pagination, narrow read projections, database-level leaderboard queries, and caching are not implemented.

## 14. Security and CORS

### Central CORS policy

`WebConfig` owns CORS for `/api/**`; controllers have no CORS overrides. Set `APP_CORS_ALLOWED_ORIGINS` to a comma-separated list of exact browser origins, for example `http://localhost:5173,https://app.example.com`. The default is empty, so cross-origin API requests are denied. Same-origin requests through the Vite or Nginx `/api/` proxy need no CORS allowlist. Root Docker Compose forwards this variable; direct JVM runs and ECS tasks must receive it through their own environment configuration.

Allowed origins support session credentials, the API's configured HTTP methods and request headers; `Content-Disposition` is exposed for CSV downloads. A wildcard origin is rejected at startup. CORS does not replace session authorization or CSRF checks and does not restrict clients such as Postman that omit the browser Origin header.

### Development mode

Authentication is enabled by default. Initialize the first Admin with `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` (at least 12 characters; maximum 72 UTF-8 bytes). Startup refuses to initialize an empty staff table without valid bootstrap credentials. Accounts are persisted with BCrypt password hashes; environment changes never overwrite an existing staff account.

For an isolated test fixture only, explicitly set `APP_REQUIRE_AUTHENTICATION=false`; this permits requests and disables CSRF. The production profile always requires authentication. Existing functional tests and the CI Compose fixture explicitly select test mode rather than weakening the application default.

### Authenticated mode

API clients use email/password sign-in and a server-side HTTP session. Login rotates the session ID; logout clears the session. Staff credential/role/status changes invalidate existing sessions on their next request. Session inactivity expires after 30 minutes. Frontend integration is separate work. Authorization rules are evaluated in this order:

| Operation | Allowed roles |
| --- | --- |
| OPTIONS/preflight | Permitted; CORS policy still applies |
| CSRF bootstrap, login, auth status and invitation validation/acceptance | Public; unsafe requests remain CSRF-protected |
| Account management | ADMIN |
| Portal profile/history/meets | Activated ATHLETE account, restricted to its one athlete |
| Athlete, event and participant notes | ADMIN, COACH; private from Athlete accounts |
| Athlete imports | ADMIN, COACH; Coach profile-changing rows become ADMIN_REQUIRED |
| Staff API reads | ADMIN, COACH |
| Basic athlete and meet writes (including archive/deletion) | ADMIN |
| Event, assignment, result and event-note writes | ADMIN, COACH |

Role names use Spring's ROLE_ prefix internally. ADMIN and COACH are staff roles; ATHLETE is a linked login account restricted to its own portal. Passwordless profile-link access is removed. Athlete accounts cannot access the staff directory, staff notes, meet management, exports or user management. The last active Admin and the acting Admin cannot be deactivated/demoted through staff management.

CSRF tokens live in the HTTP session. Before login or any other write, retrieve `/api/csrf`, retain the cookies and submit the returned token using its headerName. Refresh after authentication/logout because tokens rotate. This applies to JSON and multipart requests. HTTP Basic is no longer a login mechanism. Use HTTPS for shared deployments; the production profile sets Secure session cookies by default.

| Endpoint | Contract |
| --- | --- |
| POST `/api/auth/login` | JSON email/password; generic failure response; creates an account session |
| GET `/api/auth/me` | authenticationRequired, safe user fields and permissions; null user when not signed in with an account |
| POST `/api/auth/logout` | CSRF-protected; invalidates the session |
| GET, POST `/api/users`; PATCH `/api/users/{id}` | Admin-only account list/create/edit/deactivate; creation sends an invitation and rejects a supplied password; linked Athlete IDs are unique |
| GET `/api/portal/profile` | Own basic profile, assignments and results; no staff notes or other athletes |

All roles use POST `/api/auth/login` with email/password. Athletes and parents activate an invited ATHLETE account linked to exactly one athlete, then use the authenticated `/api/portal/**` APIs. The portal is an account-restricted view, not a second login mechanism. Ownership is revalidated from the account on each request, and availability audit records identify `athleteUserId` directly.

The passwordless athlete-link endpoints, token generator, token entity/repository and ROLE_PORTAL identity have been removed. Existing unused link tables/columns are not read by the application; no database reset or destructive cleanup is required. Account deactivation, security changes or deletion/relinking of the athlete ends access through account validation.

### Login throttling

POST `/api/auth/login` now uses shared MySQL failure counters in `login_throttle_buckets`. Defaults are five failed attempts per normalized email address and 100 failed attempts per source IP in a 900-second observation window. Reaching either threshold starts a 900-second temporary block. The threshold-reaching failure still returns the usual 401; subsequent requests return 429, including requests with the correct password, until the cooldown expires. `Retry-After` contains positive whole seconds rounded up, accounting for whichever account/IP block lasts longer. The JSON code is `AUTHENTICATION_THROTTLED` with a generic message and Cache-Control: no-store. Allowed browser origins can read Retry-After through the CORS exposed-header list.

Unknown, inactive and active accounts use the same failure/throttle response shapes. The account counter resolves the stored canonical email using the same database lookup as authentication, then trims/lowercases it. Thus database-equivalent spellings (including accent-insensitive aliases where the schema uses that collation) share a counter. Unknown emails use their normalized submitted identifier. IP counters use canonical numeric IP addresses. Only SHA-256 identifier keys and failure/window metadata are stored; passwords, raw email addresses and raw IP strings are not stored in the throttle table. A successful sign-in clears that account's failure count; it does not clear the IP's failure history. Blocked requests do not extend the cooldown, deactivate accounts or invalidate already authenticated sessions. Malformed JSON/validation errors, CORS rejections and missing/invalid CSRF are rejected before the limiter and do not consume the password-failure allowance.

Each login locks its IP bucket before its account bucket, performs local password verification, and commits failure counters before returning the credential error. This serializes simultaneous guesses for the same account/IP so they cannot race past a threshold. A blocked IP is rejected without creating more account buckets, preventing arbitrary email values from generating new rows after the source has been blocked. Authentication-provider infrastructure errors roll back counter changes. This design uses the existing database across backend instances and survives backend restarts; it adds a small database write/locking cost to login. Limits apply to login only, not ordinary authenticated API calls.

Client-address trust is explicit. `server.forward-headers-strategy=none` preserves the actual socket peer. By default `APP_LOGIN_TRUSTED_PROXIES` is empty, so client-supplied X-Real-IP, X-Forwarded-For and Forwarded headers cannot change the bucket. If the immediate peer matches a configured numeric IP/CIDR, the limiter accepts exactly one valid numeric X-Real-IP header, which the existing Nginx edge overwrites. Hostnames, ports, address lists, scoped IPv6 addresses and duplicate X-Real-IP headers fall back to the socket peer. Do not allow broad/untrusted networks. With an empty allowlist behind Nginx, the source-IP counter covers the proxy's aggregate traffic; per-account limits still apply independently. For the documented ECS setup where both containers share a task and Nginx connects over loopback, explicitly set `APP_LOGIN_TRUSTED_PROXIES=127.0.0.1/32,::1/128` and keep backend port 8080 inaccessible publicly. Separate Docker proxy containers require their actual trusted IP/CIDR; do not copy ECS's loopback setting blindly. Another upstream load balancer requires revisiting the trust chain before changing these settings.

Expired inactive buckets are deleted in bounded batches. Cleanup selects candidates, then conditionally deletes one key per short transaction, preserving recently updated counters and active blocks. The login path also refreshes expired windows/cooldowns, so cleanup timing does not determine when login becomes available again. The default cleanup interval is 60 seconds and batch size is 200. Account-based throttling intentionally permits a temporary lockout caused by repeated guesses against an email; use operational monitoring and edge protections for sustained abuse. It does not replace MFA or broader network-level rate limiting. See [OWASP authentication throttling guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#login-throttling).

The explicitly authentication-disabled CI fixture bypasses throttle storage. `APP_LOGIN_THROTTLE_ENABLED=false` is an explicit operational override; enabled is the normal default. Tests use isolated database cleanup and a controllable clock rather than sleeping through production cooldowns. No public counter-reset endpoint is provided.

### Account invitations and password setup

POST `/api/users` without a password, or POST `/api/users/invitations`, creates an invitation and queues its email. Both are Admin-only:

```json
{
  "email": "athlete@example.test",
  "name": "Invited Athlete",
  "role": "ATHLETE",
  "athleteId": 12
}
```

For Admin/Coach invitations, omit athleteId. For Athlete accounts it is mandatory and unique, including while the account is inactive or awaiting activation. Account responses include athleteId and accountStatus (INVITED, ACTIVE or INACTIVE), without activation secrets or hashes. Supplying a password when creating any account is rejected; recipients set their own passwords through invitation acceptance. Initial-Admin bootstrap is the only startup exception. Pending accounts cannot sign in or be manually activated before password setup.

The email contains a random 32-byte secret in `APP_ACTIVATION_URL#token=...`. Only its SHA-256 hash, account/security version, expiry and consumption state are stored in `user_invitations`. The default lifetime is 48 hours, configurable with APP_INVITATION_HOURS (1..168). POST `/api/users/{id}/invitation/resend` replaces earlier tokens. Pending-account deactivation, email/role/athlete-link changes invalidate outstanding tokens. Invalid, expired, superseded and reused tokens receive the same 400 unavailable response. No password is emailed.

Use the public, CSRF-protected endpoints POST `/api/auth/invitations/validate` with `{"token":"..."}` and POST `/api/auth/invitations/accept` with `{"token":"...","password":"..."}`. Passwords require 12 characters and at most 72 UTF-8 bytes and are BCrypt hashed. Acceptance activates the account; the user then signs in normally. Concurrent accepts cannot consume a token twice. Deactivating/relinking an Athlete account invalidates its sessions, and deleting its athlete record detaches and deactivates the login.

Invitation delivery uses a transactional MySQL outbox (`invitation_email_outbox`). Account, invitation and encrypted pending email are committed together. No SMTP call occurs inside that transaction. POST creation still returns 201 with accountStatus=INVITED; this means queued, not delivered. SMTP outages no longer roll back newly created accounts. A resend replaces the old token immediately and cancels older queued delivery even if SMTP is unavailable.

A scheduled worker claims due records with MySQL `FOR UPDATE SKIP LOCKED`, commits its short claim transaction, rechecks the invitation, calls the existing SMTP sender outside any database transaction, and records success or a retry in a separate transaction. A claim token fences stale acknowledgements. Expired leases can be recovered after a backend crash. Configure the lease longer than the maximum SMTP attempt duration; a pause or network operation that outlasts a lease can still lead to duplicate email.

Statuses are PENDING, PROCESSING, SENT, FAILED and CANCELLED. SENT means the SMTP server accepted the message, not proof of inbox delivery. Temporary errors use exponential backoff (defaults: 60, 120, 240, 480 seconds, capped at 3,600; at most five attempts). Authentication, invalid-recipient and malformed-payload failures are terminal. Error codes are sanitized; provider exception text, activation links and credentials are not exposed. Resend queues a fresh invitation after staff correct a failure. Pending mail for accepted, superseded, expired or invalidated invitations is skipped/cancelled. An email already in flight may still arrive; replaced links remain invalid.

The outbox stores the activation URL encrypted with AES-256-GCM, bound to the user and invitation IDs. `user_invitations` continues to store only the secret hash. Set `APP_MAIL_OUTBOX_KEY` to a private Base64-encoded 32-byte key (generate once with `openssl rand -base64 32`); retain it across backend restarts and protect it in the deployed secret store. Production startup refuses a missing or invalid key. In non-production the backend can start without it, but invitation creation receives 503 until configured and the worker leaves existing queued messages untouched. Ciphertext is erased on SENT/FAILED/CANCELLED. Do not change the key while messages are pending or processing; key rotation requires draining/cancelling those messages first. Never log decrypted payloads or enable SMTP protocol debug in production.

Admin-only `GET /api/users/{id}/invitation/delivery` returns the latest delivery metadata: id, userId, invitationId, status, attempts, maxAttempts, nextAttemptAt, claimedUntil, lastErrorCode, createdAt, updatedAt and sentAt. It returns 404 for an unknown account or an account without an outbox record, including invitations created before this release. It never returns the encrypted payload, activation URL or secret. Responses use Cache-Control: no-store. Existing POST `/api/users/{id}/invitation/resend` is the staff recovery operation. All these routes require ADMIN in secured mode.

Delivery is at least once: if SMTP accepts a message and the backend crashes before recording SENT, a retry can send a duplicate. Token acceptance remains single-use. Queue durability depends on a persistent database; the disposable Postman MySQL fixture uses tmpfs and is intentionally erased by stopping/removing that MySQL container.

Configure SMTP_HOST, SMTP_PORT, SMTP_FROM, and, where required, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH and SMTP_STARTTLS. Production SMTP configuration must use the chosen provider's settings; Mailpit only captures mail locally. No cloud email provider is provisioned by this change.

For a local email inbox, start:

```sh
docker compose --profile mail up --build mysql backend mailpit
```

Open `http://localhost:8025`. The optional Mailpit service captures SMTP internally on port 1025 and publishes its inbox only on localhost. See [Mailpit's Docker documentation](https://mailpit.axllent.org/docs/install/docker/). No activation page is implemented: backend/Postman clients copy the token from the captured link and call the validation/acceptance APIs. APP_ACTIVATION_URL is the future client route, not an added frontend screen. The [invitation Postman collection](../docs/postman/flying-angels-invitations.postman_collection.json) automates this through the local inbox API with synthetic recipients.

### Browser origin policy

`WebConfig` permits credentialed requests only from exact origins configured through `APP_CORS_ALLOWED_ORIGINS`, methods GET/POST/PUT/PATCH/DELETE/OPTIONS, and request headers. The default origin list is empty. The supplied frontend uses a same-origin `/api` proxy. A separately hosted frontend needs its exact trusted origin configured; wildcard credentialed CORS is not enabled.

CORS controls browser access; it does not replace authentication or restrict a non-browser HTTP client.

## 15. Configuration and environment variables

### Default application settings

| Setting | Current default |
| --- | --- |
| Application name | backend |
| HTTP port | 8080 |
| JDBC URL | `jdbc:mysql://mysql:3306/flying_angels?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC` |
| Default DB username/password | root / empty password; local Compose overrides these using `.env` |
| Hibernate schema action | validate; Flyway applies versioned SQL changes before JPA initialization |
| Open EntityManager in View | false |
| SQL logging | enabled in default profile |
| Max file / multipart request | 10 MB / 11 MB |

### Common overrides

| Environment variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Override JDBC URL for local/Compose execution |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SERVER_PORT` | Change backend HTTP port |
| `APP_AGE_GROUP_BANDS` | Inclusive club-defined bands as `label:minAge:maxAge;label:minAge:maxAge`; empty means unconfigured |
| `APP_AVAILABILITY_TIME_ZONE` | Meet-local clock and response cutoff zone; defaults to America/Toronto |
| `APP_ACTIVATION_URL`, `APP_INVITATION_HOURS` | Client activation-link route and token lifetime |
| `APP_MAIL_OUTBOX_KEY` | Private Base64-encoded 32-byte encryption key; mandatory in prod; retain across restarts |
| `APP_MAIL_OUTBOX_ENABLED`, `APP_MAIL_OUTBOX_POLL_MS` | Scheduled processing toggle and interval (defaults true / 5000 ms); disabled processing still queues mail |
| `APP_MAIL_OUTBOX_BATCH_SIZE`, `APP_MAIL_OUTBOX_MAX_ATTEMPTS` | Per-poll send batch and attempt limit (20 / 5) |
| `APP_MAIL_OUTBOX_RETRY_BASE_SECONDS`, `APP_MAIL_OUTBOX_RETRY_MAX_SECONDS` | Exponential retry base/cap (60 / 3600 seconds) |
| `APP_MAIL_OUTBOX_LEASE_SECONDS` | Claim recovery timeout (120 seconds); must exceed SMTP attempt duration |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` | Invitation email transport and sender |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` | Optional SMTP authentication and required STARTTLS settings |
| `SPRING_PROFILES_ACTIVE` | Select `prod` when required |
| `APP_SECURITY_REQUIRE_AUTHENTICATION` | Override authentication mode through Spring property binding |
| `APP_REQUIRE_AUTHENTICATION` | Explicit default-profile override; false is reserved for isolated test fixtures |
| `APP_LOGIN_THROTTLE_ENABLED` | Enable login throttling (default true; authentication-disabled CI bypasses it) |
| `APP_LOGIN_THROTTLE_ACCOUNT_LIMIT`, `APP_LOGIN_THROTTLE_IP_LIMIT` | Failed-attempt thresholds per normalized account/source IP (5 / 100) |
| `APP_LOGIN_THROTTLE_WINDOW_SECONDS`, `APP_LOGIN_THROTTLE_BLOCK_SECONDS` | Observation window and temporary block duration (900 / 900 seconds) |
| `APP_LOGIN_TRUSTED_PROXIES` | Explicit immediate-proxy numeric IP/CIDR allowlist; empty trusts no forwarding headers |
| `APP_LOGIN_THROTTLE_CLEANUP_ENABLED`, `APP_LOGIN_THROTTLE_CLEANUP_MS` | Scheduled expired-counter cleanup (true / 60000 ms) |
| `APP_ADMIN_EMAIL` | Email to create the first persisted Admin; legacy APP_ADMIN_USERNAME is accepted only when it contains a valid email |
| `APP_ADMIN_PASSWORD` | Bootstrap password for an empty staff table; at least 12 characters |
| `DATABASE_URL` | JDBC URL read by prod properties |
| `DATABASE_USERNAME` | Database user read by prod properties |
| `DATABASE_PASSWORD` | Database password read by prod properties |

Production additionally disables SQL logging. Docker Compose does not automatically enable the prod profile or inject these production variables; its current environment block supplies development database settings explicitly.

## 16. Running and building

### Prerequisites

Use a JDK compatible with the Java 17 compilation target, a reachable MySQL database, and network access for the first Maven-wrapper/dependency download. Docker is needed for Compose and database-backed tests. Local Node/npm is not needed to run the backend.

Commands in this section use a POSIX shell. On Windows, use `mvnw.cmd` with equivalent environment configuration.

### Docker Compose

From the `Flying-Angels` repository root:

On first setup, copy `.env.example` to `.env` and set `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD`. Compose automatically loads this private Git-ignored file and uses the same application username/password for MySQL and the backend.

```sh
docker compose up --build mysql backend
```

For the full development stack:

```sh
docker compose up --build
```

The MySQL health check gates backend startup and reads its root password from the container environment. MySQL uses the named `mysql_data` volume at `/var/lib/mysql`, preserving records across container restarts/recreation. Frontend listens on 5173, backend on 8080, and MySQL on host port 3306. Starting only `mysql backend` does not start the frontend. Compose reads database credentials from `.env`. Frontend/backend share the `application` bridge; backend/MySQL share the `database` bridge. Published host ports remain separate access paths. See [local Docker setup](../docs/local-docker.md).

Check the API and logs:

```sh
curl http://localhost:8080/api/athletes
docker compose logs backend
```

No Actuator health endpoint is configured. An empty athlete database normally returns `[]` from the directory endpoint.

### Run the backend directly on the host

Start only the database from the repository root:

```sh
docker compose up -d mysql
```

Then, from `backend/`, configure host-accessible database settings:

```sh
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/flying_angels?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true' \
SPRING_DATASOURCE_USERNAME='YOUR_MYSQL_USER_FROM_ENV' \
SPRING_DATASOURCE_PASSWORD='YOUR_MYSQL_PASSWORD_FROM_ENV' \
bash mvnw spring-boot:run
```

Replace the two placeholders with the values from your private `.env`, or configure equivalent environment variables in your IDE. Compose loads `.env` automatically; a bare Maven process does not. Use your actual credentials for another database. Avoid running both host and container backends on the same HTTP port.

### Build and package

From `backend/`:

```sh
bash mvnw compile
bash mvnw test
bash mvnw package
```

The packaged application is `target/backend-0.0.1-SNAPSHOT.jar`:

```sh
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Database variables still apply when running the JAR. To use production settings, provide all five production account/database variables listed above and select `SPRING_PROFILES_ACTIVE=prod`. Flyway applies pending migrations during startup, then Hibernate validates the schema before normal request handling.

## 17. Database schema management

Flyway owns schema changes; Hibernate checks the result:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.locations=classpath:db/migration
spring.flyway.target=latest
spring.flyway.baseline-on-migrate=false
spring.flyway.clean-disabled=true
```

A fresh MySQL database runs `V1__application_schema.sql`, containing all 16 current tables, including the invitation email outbox and login-throttling counters. Flyway records successful versions and checksums in `flyway_schema_history`. Later startups validate applied scripts and run only pending versions. CSV imports remain explicit API operations.

An existing non-empty database without Flyway history stops by default. After a backup and manual review against V1, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` with `SPRING_FLYWAY_BASELINE_VERSION=1` for one startup. Standard Flyway records that starting version; there is no custom schema verifier. Hibernate checks entity compatibility afterward. A different older schema requires a reviewed conversion before baselining. Reset baseline-on-migrate to false afterward.

For the next persisted change, add V2 and update the entity mappings together. Do not edit applied migration files. Setting a lower target does not undo earlier changes; the application version must remain compatible with the selected schema. MySQL DDL is not guaranteed to roll back as a unit, so test upgrades with representative records and maintain backups.

See [Database migrations](../docs/database-migrations.md) for **every variable**, adoption commands, version selection, rollback behavior and RDS rollout precautions. [ADR-0012](../docs/adr/0012-versioned-database-migrations.md) supersedes the earlier Hibernate-update decision.

## 18. Testing and verification

### Test organization

| Test | Coverage |
| --- | --- |
| `AthleteServiceTest` | Input normalization and athlete lookup/service behavior with mocked repositories |
| `AthleteControllerTest` | Standalone MockMvc status/validation/error contracts |
| `AthleteControllerWebMvcTest` | MVC slice, profile/directory responses and malformed IDs |
| `AthleteDirectoryIntegrationTest` | Real database search and filter behavior |
| `AgeRangeTest`, `AthleteAgeFilteringIntegrationTest` | Exact birthday/leap-day age, supplied reference date and combined directory filters |
| `AthleteProfileIntegrationTest` | Own-result isolation, historical values and profile correctness |
| `AthleteRegistrationParserSpreadsheetTest` | Shared TSV/XLSX parsing, typed DOB, rejected missing DOB and preserved Excel duration formatting |
| `AthleteImportUpdateIntegrationTest` | Required DOB across preview/commit/immediate routes, legacy preview revalidation, mixed time/distance profile results, updates, blank optional fields and retries |
| `AthleteImportControllerTest` | Import endpoint validation and HTTP errors |
| `ImportPreviewValidationIntegrationTest` | Empty/header-only files, empty selections, repeated registrations, accented names, cancellation and commit-time identity rechecks |
| `AthleteRegistrationParserTest` | Header mapping, club format, warnings and row errors |
| `ResultValuesTest` | Track/field normalization, statuses, precision/format mismatches |
| `LoginThrottleIntegrationTest`, `LoginClientAddressTest` | Account/IP limits, concurrency, cooldown/observation-window expiry, successful-login reset, restart persistence, trusted proxy handling, cleanup and test-mode bypass |
| `InvitationMailOutboxIntegrationTest`, `InvitationMailCipherTest` | Atomic rollback, uncommitted isolation, encryption/tampering, retry timing/limits, stale leases, concurrent claims, safe Admin status and lifecycle cancellation |
| `EndpointRoleMatrixIntegrationTest` | All 84 API endpoints under four identities, complete route inventory, 160 missing-CSRF checks and shared staff note persistence/privacy |
| `ManagementIntegrationTest` | Independent participation, attempts, rollback, copy, notes, imports, deletes and consistent updates |
| `EventParticipationIntegrationTest` | Empty/duplicate/missing athlete selections, assignment rollback, multiple events and scoped result deletion |
| `ResultEditingIntegrationTest` | Atomic bulk PUT, existing result identity, malformed values and wrong-event/not-found records |
| `ResultsOverviewIntegrationTest` | Cross-meet filters, age at meet start, discipline-appropriate performance sorting, all attempts and pagination |
| `RankingsIntegrationTest` | Cross-meet best selection, score direction, age/gender filters, ties across pages, canonical PB representatives, invalid/legacy values, empty states and live edits/deletion |
| `RankingsSecurityTest`, `AuthenticationIntegrationTest` | Staff-only ranking context, real scoped portal projections, own-profile access, forged-scope rejection and account deactivation |
| `AvailabilityIntegrationTest`, `AvailabilitySecurityTest`, `AuthenticationIntegrationTest` | Meet-level deduplication, response audit/changes, stale revisions, concurrent assignments/replies, atomic rollback, startup backfill, removals/deletion, past meets, CSRF and real scoped portal access |
| `MeetAvailabilityOverviewIntegrationTest`, `MeetOverviewSecurityTest` | Upcoming/Past/All views, club-date boundaries, legacy date fallback, search, distinct-athlete summaries, stale response coverage, combined filters, stable paging, bounded query counts and staff-only access |
| `MeetExportIntegrationTest`, `MeetExportSecurityTest`, `AuthenticationIntegrationTest` | Registration/result row granularity, repeated attempts, empty exports, CSV Unicode/escaping/formula handling, download headers, query bounds, scope, no data mutation and real staff/portal permissions |
| `ProductionSecurityTest` | Authentication, role restrictions and CSRF enforcement |
| `BackendApplicationTests` | Flyway creates every current table and history on empty MySQL; Hibernate validates the mappings |
| `DatabaseMigrationIntegrationTest` | Fresh V1 schema; explicit baseline with existing records; refusal without a baseline; repeat startup; checksums; disabled clean; draft upgrade from the previous committed schema with data preservation |
| `DatabaseIntegrationTest` | Shared MySQL container setup and child-before-parent cleanup for integration subclasses |

The completed working-tree verification on 2026-10-30 recorded **127 JUnit tests passing**, using real MySQL for schema-startup and integration tests. The functional Newman run also passed **139 requests and 226 assertions** against a fresh temporary database. This includes import preview/selection guards, exact age filtering, participation removal scope, atomic result corrections and Results Overview, alongside the existing identity and measurement tests. See [execution evidence](../docs/postman/verification.md) for the tested base commit, profiles and limits; the separate security collection was not rerun in this update.

### Commands

From `backend/`:

```sh
bash mvnw test
bash mvnw clean test
bash mvnw -Dtest=ResultValuesTest,AthleteRegistrationParserTest test
```

On this development machine, the installed Docker daemon requires a newer API version than the Testcontainers client's default:

```sh
bash mvnw -Dapi.version=1.44 test
```

Use that override if the failure says the Docker client API version is too old. Docker must be running and accessible; images may need downloading. Tests use disposable databases, not the Compose database volume. Test reports are under `target/surefire-reports/`.

Mocked service tests cannot establish MySQL collation, JPQL filtering, foreign-key or schema-generation behavior. Keep the real-database tests when changing these areas.

## 19. Frontend integration

The existing React application exposes an athlete directory and profile. Its API module is [athleteService.ts](../frontend/src/services/athleteService.ts), and shared types are in [athlete.ts](../frontend/src/types/athlete.ts).

Clients should:

- Handle null historical result/category IDs, scores and meet/event context.
- Use `participations[]` to display entered events even when no performance exists.
- Use result POST to append and result-ID PUT to correct.
- Use the import preview's returned row numbers and explanations rather than recomputing them.
- Interpret SKIPPED/import counts according to the job-wide behavior above.
- Read backend error codes/messages when presenting failures.
- Handle secure-mode authentication and CSRF if deployed with that mode.

The current frontend service handles athlete CRUD; dedicated meet, participation, result-entry, import-review and note-history screens are not fully wired. Backend endpoint availability does not imply those frontend workflows already exist. Some existing frontend calls still refetch a profile after PUT even though the updated backend response contains the current profile.

The frontend build and its 27 tests passed during the preceding backend implementation verification.

## 20. Troubleshooting

| Symptom | Check / explanation |
| --- | --- |
| Host backend cannot resolve `mysql` | Use localhost in JDBC URL when running outside Compose |
| DB connection refused or access denied | Confirm MySQL health, host/port and the active profile's credentials |
| Port 8080 already in use | Stop the duplicate development runner or choose SERVER_PORT |
| Migration or schema validation fails | Check Flyway logs, datasource, CREATE/ALTER privileges and migration history; an unversioned populated DB needs a backup, schema review and explicit baseline. Keep ddl-auto=validate |
| `./mvnw` permission denied | Use `bash mvnw ...` as shown, or deliberately set wrapper execution permission |
| Docker tests report old client API | Use the tested `-Dapi.version=1.44` override for this environment |
| Secure GET returns 401 | Sign in through POST /api/auth/login and retain the session cookie; check account status and bootstrap settings |
| Secure write returns 401/403 | Check the staff role, session cookie and fresh CSRF token. Basic Auth is not supported; absent/invalid CSRF yields 403 |
| Browser request fails CORS | Current allowed origin is localhost:5173; proxy or configure production origin/credentials |
| CSV import cannot find first/last name | Confirm the first record is the actual header and delimiter is comma/tab |
| Import row has missing/invalid DOB | Supply a valid past ISO date or typed XLSX date and create a new preview; invalid rows cannot be saved |
| Qualifier or malformed result is UNPARSED | Source is retained; parsing rules intentionally do not guess its meaning |
| Import row is UNCHANGED | No supplied information differs; skipped without modifying stored data |
| Import row is AMBIGUOUS | Several identities share the name; supply an identifier or resolve the match |
| Imported total does not equal selection size | Summary is job-wide and includes previously imported rows |
| Event's participantCount differs from result count | One assignment can have zero or multiple attempts |
| Historical result cannot be corrected with a meet-event route | Event-less historical records have no matching event path; no dedicated historical-result correction API exists |

## 21. Current limitations and extension points

These are boundaries of the current implementation, not features already delivered:

- Recorded competition personal bests and chronological progression are available through athlete history. Official-result verification, official PB eligibility, seasons, age-category calculation, wind conditions and indoor/outdoor ranking distinctions still need club-approved rules and additional services/data.
- The event identity rule does not model heats, rounds or divisions. Extend the constraint and DTOs together if these are needed.
- Performance records have optional descriptive result labels, but no structured attempt number, result-version field or full correction audit trail; individual IDs distinguish attempts.
- Team filtering interprets populated status rather than membership meaning. Status text remains open-ended.
- External registration ID reliability is assumed only within a supplied source; no external identity verification runs.
- Demographic matching can be ambiguous and is not safe against all concurrent duplicate creation.
- Both legacy and central performance stores remain during compatibility support. A complete migration to one store requires a deliberate reconciliation policy.
- Most collection endpoints lack pagination. Large reads and large parsed files consume application memory.
- Import previews are synchronous and reparsed on retrieval; there is no queue, progress polling state machine, cancellation or original-file download.
- Import source files/outcomes persist without a configured cleanup/retention mechanism.
- Only the first XLSX worksheet is imported. Administrative columns remain in original bytes but are not extracted as separate metadata.
- There is no club-account lifecycle, password-reset API, JWT/OAuth integration, audit author tracking, or access control per individual athlete/meet.
- Frontend login, role-aware navigation and athlete-link screens are not included; clients must integrate the new session/CSRF APIs. Separately hosted clients also need an exact trusted production CORS origin.
- No Actuator, metrics/tracing setup, application cache, OpenAPI generator or notification service is configured in the current dependencies.

## 22. Design decisions and maintenance guide

### Why these boundaries were chosen

| Decision | Reason / consequence |
| --- | --- |
| One application with feature packages | Keeps deployment simple while allowing transactional cross-feature workflows |
| Separate definition and meet event | A reusable 100m definition can appear in different meets without copying athlete assignments |
| Separate participation and result | Entering an athlete does not require a fake result; multiple performances remain possible |
| Raw result plus normalized score | Preserves source evidence while supporting numeric comparison |
| Historical event-less results | Avoids falsely assigning roster performances to a competition |
| Dedicated note history | Adds timestamped observations without overwriting the existing general note |
| Single import module/service | Both upload routes use the same create/update/skip policy; athlete CRUD contains no file-import orchestration |
| Separate row-writer bean | Makes per-row rollback effective through Spring's transaction proxy |
| DTO responses | Avoids exposing recursive JPA relationships and keeps client contracts explicit |
| Flyway SQL with Hibernate validation | Reviews schema changes explicitly and checks that the resulting database matches entity mappings |
| Compatibility routes/fields retained | Reduces disruption to current athlete screens and older callers |

### Adding or changing a feature

1. Define the business rule and whether existing data/client contracts are affected.
2. Add/update DTO constraints and explicit response fields.
3. Put workflow rules in the appropriate feature service; keep controller code focused on HTTP concerns.
4. Add repository queries/entity-graph paths that satisfy response reads with Open-in-View disabled.
5. Add the next Flyway SQL migration, update entity mappings, and verify fresh creation plus populated upgrades against disposable MySQL.
6. Add tests for the business outcome, transaction rollback and database constraint where relevant.
7. Check production authorization/CSRF behavior for any new route.
8. Update this guide and the relevant editable/SVG code diagram.

When changing result parsing, test track times, field units, non-performance statuses and preserved malformed imports. When changing identity rules, test missing DOB, repeated rows, source IDs and retries. When changing deletion, test child records and note preservation.

Use database constraints for invariants that must survive competing requests; service checks provide earlier, clearer errors. Avoid adding a blanket cascade to athlete/result relationships without checking historical-data consequences.

## 23. Related documentation and diagrams

- [Backend workflows: which endpoint to use and when](../docs/backend-workflows.md): business-task mapping, preview/commit lifecycle and real-client CSV testing.
- [Postman test pack and complete endpoint checklist](../docs/postman/README.md): ordered functional/security requests, synthetic uploads and verification evidence.
- [Architecture Decision Records](../docs/adr/README.md): current design decisions, alternatives, trade-offs and team-review status.

- [Backend change rationale](../docs/backend-changes.md): focused summary of the preceding implementation changes.
- [API documentation entry point](../docs/athlete-api.md).
- [Diagram index](../docs/diagrams/README.md).
- [Editable draw.io document](../docs/diagrams/use-case-code-diagrams.drawio): all seven use cases.
- [Meet management SVG](../docs/diagrams/01-meet-management.svg).
- [Event management SVG](../docs/diagrams/02-event-management.svg).
- [Participation SVG](../docs/diagrams/03-athlete-participation.svg).
- [Results SVG](../docs/diagrams/04-results-management.svg).
- [Notes SVG](../docs/diagrams/05-athlete-notes.svg).
- [Import SVG](../docs/diagrams/06-athlete-import.svg).
- [Athlete API SVG](../docs/diagrams/07-athlete-api.svg).

The diagram generator is [generate.py](../docs/diagrams/generate.py). From the repository root, run `python3 docs/diagrams/generate.py`; it produces SVGs and the editable draw.io file. Separate Mermaid `.mmd` outputs have been removed and are no longer generated.
