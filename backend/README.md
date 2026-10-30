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
| Operations | Hibernate-managed schema, automated tests, configurable authenticated production access |

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

There is currently no personal-best service, season-ranking API, official-result verification workflow, Trackie connector, or club user-account database. Those features must not be inferred from the existence of an event leaderboard.

## 2. Technology stack

Versions below are taken from [pom.xml](pom.xml) and the repository's runtime configuration.

| Technology | Purpose |
| --- | --- |
| Java 17 language target | Backend source and compilation target in Maven |
| Spring Boot 4.1.1 | Application startup, configuration, web and persistence integration |
| Spring Web MVC | REST controllers and JSON request/response handling |
| Spring Data JPA / Hibernate | Entity persistence, repositories, schema creation/update, entity graphs, dirty checking |
| Spring Security | HTTP Basic authentication, role authorization, CSRF and CORS integration |
| Jakarta Validation | DTO field validation and nested request validation |
| MySQL 8.4 | Relational database used by Docker Compose and integration tests |
| Apache Commons CSV 1.14.1 | CSV and tab-delimited text parsing |
| Apache POI OOXML 5.4.1 | XLSX workbook parsing and typed Excel date handling |
| Lombok | Generated getters, constructors, and dependency-injection constructors |
| Maven Wrapper | Consistent Maven invocation without a separate Maven installation |
| JUnit, Mockito, Spring test / MockMvc | Unit tests, HTTP contract tests, and service testing |
| Testcontainers 1.21.3 | Disposable real MySQL databases for startup and integration tests |

The current backend Dockerfile uses **Eclipse Temurin JDK 21**, while Maven targets Java 17. It runs Maven's `spring-boot:run` inside the container. It is a development-oriented runner rather than a minimal packaged-JAR production image.

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
| Shared infrastructure | Configure security/CORS, normalize API errors, configure entity-generated schema | `SecurityConfig`, `ApiExceptionHandler`, Hibernate settings |

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
| `EventResult` / `event_results` | ID, optional event ID, category/athlete IDs, raw result, decimal score, unit/status, recorded time, source | Multiple rows for an athlete/event are allowed; numeric score is `DECIMAL(12,4)` |
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

The entity retains `meetDate` as the effective start date. Meet lists sort newest meet date first, then name. Updating a meet cannot leave existing event dates outside its new range.

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
  "unit": "SECONDS"
}
```

`POST /api/events/{id}/results` appends a record and creates participation if necessary. Repeating the POST can create another attempt; there is no result-request idempotency key.

`PUT /api/events/{id}/results/{resultId}` corrects one record. Its request can also change the athlete; the service creates participation for the replacement athlete if needed. The original athlete's assignment is not automatically removed.

The compatibility route `PUT /api/events/{id}/participants/{athleteId}/result` requires an existing assignment and **appends** a result. Its behavior is not idempotent despite using PUT; new clients should use POST results for new performances.

The result-batch wrapper is `{"results": [...]}`. One invalid record rolls back the complete batch. Bulk POST creates attempts. New `PUT /api/events/{id}/results/bulk` corrects existing attempts using `resultId`, `result`, optional `score` and `unit`; it preserves athlete/event ownership, IDs and recording timestamps. See [bulk editing and Results Overview](../docs/sprint2-api-updates.md).

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

## 9. Athlete notes

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

There are no optimistic-lock version fields or general request idempotency keys. Demographic matching without a stable external ID is not database-unique and can still race between jobs.

### Fetching and DTO construction

`spring.jpa.open-in-view=false` disables persistence access throughout the web view/serialization phase. Repositories use entity graphs for required event/category/athlete/meet context; services assemble richer athlete and note responses while in transactions.

Event result and participation collections use sets so fetching both avoids the multiple-bag problem of simultaneously fetching two unindexed JPA lists. Do not rely on those sets to provide a stable chronological API order. Notes and participant lists have explicit ordering; result lists generally do not.

Large event/meet reads can fetch substantial related data. Pagination, narrow read projections, database-level leaderboard queries, and caching are not implemented.

## 14. Security and CORS

### Development mode

By default, `app.security.require-authentication` resolves to false. All requests are permitted and CSRF is disabled. This supports the existing local frontend workflow.

### Authenticated mode

The `prod` profile enables the property and uses Spring Security HTTP Basic authentication. Authorization rules are evaluated in this order:

| Operation | Allowed roles |
| --- | --- |
| OPTIONS/preflight | Permitted; CORS policy still applies |
| Any athlete-import route, including preview GET | ADMIN |
| DELETE under `/api/**` | ADMIN |
| Other GET under `/api/**` | ADMIN, COACH, VIEWER |
| Other API writes | ADMIN, COACH |
| Other paths | Authenticated |

Role names are Spring roles with the normal ROLE_ prefix internally. The shipped production properties configure one bootstrap ADMIN account through environment variables. No database-backed account provisioning or role-management endpoints exist.

CSRF remains enabled with a cookie token repository. An authenticated write client retrieves `/api/csrf`, retains its cookies/session, and submits the returned token using the indicated header name. HTTP Basic credentials alone do not authorize a write request lacking a CSRF token. Use HTTPS for a shared deployment.

### Browser origin policy

`WebConfig` currently permits `http://localhost:5173` for `/api/**`, methods GET/POST/PUT/DELETE/OPTIONS, and any request header. The configuration is hard-coded and does not enable cross-origin credentials. A separately hosted production frontend requiring cookies/authentication needs deliberate CORS configuration, or a same-origin proxy deployment.

CORS controls browser access; it does not replace authentication or restrict a non-browser HTTP client.

## 15. Configuration and environment variables

### Default application settings

| Setting | Current default |
| --- | --- |
| Application name | backend |
| HTTP port | 8080 |
| JDBC URL | `jdbc:mysql://mysql:3306/flying_angels?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC` |
| Default DB username/password | root / empty password; Compose overrides these |
| Hibernate schema action | update; creates missing tables/columns from entity mappings |
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
| `SPRING_PROFILES_ACTIVE` | Select `prod` when required |
| `APP_SECURITY_REQUIRE_AUTHENTICATION` | Override authentication mode through Spring property binding |
| `APP_ADMIN_USERNAME` | Required bootstrap username when using supplied prod settings |
| `APP_ADMIN_PASSWORD` | Required bootstrap password when using supplied prod settings |
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

```sh
docker compose up --build mysql backend
```

For the full development stack:

```sh
docker compose up --build
```

The MySQL health check gates backend startup. For local import testing, MySQL currently uses a temporary `tmpfs` mount at `/var/lib/mysql`: its database contents are lost whenever the MySQL container stops or restarts. Recreate the containers after changing storage configuration (`docker compose down`, then the startup command above). An existing `mysql_data` volume is not mounted or deleted by this configuration. Frontend listens on 5173, backend on 8080, and MySQL on host port 3306. Compose supplies the backend with its development app database credentials.

To restore persistent storage, replace the MySQL mount with `- mysql_data:/var/lib/mysql`, restore the top-level `volumes: { mysql_data: {} }` declaration, and recreate the containers. Use a persistent configuration for data you intend to retain.

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
SPRING_DATASOURCE_USERNAME=appuser \
SPRING_DATASOURCE_PASSWORD=apppassword \
bash mvnw spring-boot:run
```

Those credentials match the checked-in development Compose file. Use your actual credentials for another database. Avoid running both host and container backends on the same port.

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

Database variables still apply when running the JAR. To use production settings, provide all five production account/database variables listed above and select `SPRING_PROFILES_ACTIVE=prod`. Hibernate creates/updates the schema from entity mappings during startup before normal request handling.

## 17. Database schema management

The application starts with an empty MySQL database and loads athlete data through the CSV/TSV/XLSX importer. Hibernate manages the current schema directly from the JPA entities:

```properties
spring.jpa.hibernate.ddl-auto=update
```

At startup Hibernate creates missing tables/columns and applies supported mapping changes. Table names, relationships, unique constraints, result precision, column lengths and the note-order index are declared in entity annotations. There are no SQL migration files or migration-runner dependencies. The decision is documented in [ADR-0006](../docs/adr/0006-hibernate-schema-management.md).

### Local workflow

1. Start MySQL and the backend.
2. Hibernate creates the current tables, including participation, notes and import jobs/rows.
3. Upload a client file, review the preview, and commit selected eligible rows.
4. Use the meet, event, participation, result and note APIs normally.

Restarting only the backend keeps the existing database rows. The current Compose MySQL mount is temporary: stopping/restarting MySQL clears its data, and the next backend startup creates fresh tables. CSV importing is an explicit API operation; startup does not automatically import the client file.

For a persisted schema change, update the appropriate entity mapping and verify it against disposable MySQL. Hibernate schema update does not perform custom historical-data conversions; this sprint assumes a fresh initial database rather than an older deployed client schema.

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
| `ManagementIntegrationTest` | Independent participation, attempts, rollback, copy, notes, imports, deletes and consistent updates |
| `EventParticipationIntegrationTest` | Empty/duplicate/missing athlete selections, assignment rollback, multiple events and scoped result deletion |
| `ResultEditingIntegrationTest` | Atomic bulk PUT, existing result identity, malformed values and wrong-event/not-found records |
| `ResultsOverviewIntegrationTest` | Cross-meet filters, age at meet start, discipline-appropriate performance sorting, all attempts and pagination |
| `ProductionSecurityTest` | Authentication, role restrictions and CSRF enforcement |
| `BackendApplicationTests` | Hibernate creates every current table and the note-order index on empty MySQL, without a migration-history table |
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
| Tables are missing or schema update fails | Confirm ddl-auto=update, the active datasource and database account CREATE/ALTER privileges; inspect Hibernate startup logs |
| `./mvnw` permission denied | Use `bash mvnw ...` as shown, or deliberately set wrapper execution permission |
| Docker tests report old client API | Use the tested `-Dapi.version=1.44` override for this environment |
| Secure GET returns 401 | Supply credentials and verify required production account variables |
| Secure write returns 401/403 | Check credentials, role and CSRF token/cookie handling; live Basic-auth writes without valid CSRF currently return 401, while pre-authenticated role/CSRF tests can return 403 |
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

- Official-result verification, personal-best eligibility, seasons, age-category calculation, wind conditions and indoor/outdoor ranking distinctions need club-approved rules and additional services/data.
- The event identity rule does not model heats, rounds or divisions. Extend the constraint and DTOs together if these are needed.
- Performance records have no explicit attempt number, result-version field or full correction audit trail; individual IDs distinguish attempts.
- Team filtering interprets populated status rather than membership meaning. Status text remains open-ended.
- External registration ID reliability is assumed only within a supplied source; no external identity verification runs.
- Demographic matching can be ambiguous and is not safe against all concurrent duplicate creation.
- Both legacy and central performance stores remain during compatibility support. A complete migration to one store requires a deliberate reconciliation policy.
- Most collection endpoints lack pagination. Large reads and large parsed files consume application memory.
- Import previews are synchronous and reparsed on retrieval; there is no queue, progress polling state machine, cancellation or original-file download.
- Import source files/outcomes persist without a configured cleanup/retention mechanism.
- Only the first XLSX worksheet is imported. Administrative columns remain in original bytes but are not extracted as separate metadata.
- There is no club-account lifecycle, password-reset API, JWT/OAuth integration, audit author tracking, or access control per individual athlete/meet.
- Production CORS and frontend login/CSRF integration need deployment-specific configuration.
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
| Hibernate entity-generated schema | Keeps fresh-database setup simple; mappings define the current tables and constraints |
| Compatibility routes/fields retained | Reduces disruption to current athlete screens and older callers |

### Adding or changing a feature

1. Define the business rule and whether existing data/client contracts are affected.
2. Add/update DTO constraints and explicit response fields.
3. Put workflow rules in the appropriate feature service; keep controller code focused on HTTP concerns.
4. Add repository queries/entity-graph paths that satisfy response reads with Open-in-View disabled.
5. Update entity mappings for persisted changes and verify schema generation against disposable MySQL.
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
