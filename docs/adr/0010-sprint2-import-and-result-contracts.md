# ADR-0010: Validate import selections and support age-filtered result review

- Recorded: 2026-10-30
- Status: **Implemented; awaiting team ratification**
- Scope: Registration preview/commit, athlete selection, event participation and competition results
- Related use cases: CSV Registration Import; Athlete-to-Meet Event Participation; Athlete Competition Results & Performance Record Management
- Basis: The project owner authorized these acceptance-gap changes and confirmed exact age at meet start and deletion of event results when participation is removed.
- Reviewer / approval date: Team ratification has not been recorded.

## Context

Header-only uploads created empty previews, and empty commit selections returned a successful no-op. Repeated registrations in a file were not clearly marked before confirmation. The application also needed an agreed age reference, a clear participation-removal consequence, an atomic operation for Edit All Results and a search endpoint for Results Overview across meets.

## Decision

1. Reject uploads with no athlete records and reject empty commit selections with 400. Do not save an import job for a no-record upload.
2. During preview, identify repeated registrations using the supplied identifier, otherwise case-insensitive name and exact DOB. Mark identical parsed rows DUPLICATE with `repeatedOfRowNumber`; skip them even if selected. Invalid rows do not suppress later valid rows. Existing AMBIGUOUS identity blocks remain blocked.
3. Preserve meaningful repeated rows with different supplied information or performance values. Show their repeated-row reference, retain their normal classification and revalidate eligible selected rows in source order at commit. Do not remove identifier-confirmed upserts or historical performance preservation.
4. Calculate completed years on the meet's start date for competition filtering. Directory filtering requires a supplied `ageAsOf` date with inclusive optional `minAge`/`maxAge` bounds. A February 29 birth advances on March 1 in a non-leap year. Unknown DOBs are excluded only when age filtering is active. The frontend maps agreed age-group labels to bounds; the backend does not invent athletics categories.
5. Keep the owner-confirmed removal policy: deleting one athlete's participation deletes all of that athlete's results for that event. Preserve other events, athletes and assignments. The frontend confirmation must explain this effect.
6. Add atomic `PUT /api/events/{id}/results/bulk` for existing result IDs, distinct from bulk POST creation. Validate the complete batch before mutation; preserve IDs, athlete/event ownership, source and recording timestamps. One invalid or missing result rejects the whole batch.
7. Add paged read-only `GET /api/results` with athlete-name search and meet, event instance, reusable definition, gender and age filters. Return all attempts with meet/athlete/event context. Performance sorting groups by event definition and applies its configured direction, with valid scores before unscored outcomes. Do not compare numerical scores across disciplines or invent meet context for imported historical values.

## Alternatives considered

- Treat every repeated identity as excluded: would lose meaningful updates and additional performance history requested for this project.
- Automatically commit the original when only a duplicate row is selected: would apply an administrator's unselected row.
- Calculate age today or at the event date: does not meet the confirmed meet-start rule.
- Preserve results after participation removal: rejected by the owner's explicit removal choice.
- Implement Edit All as several independent PUTs: permits partial completion when one edit fails.
- Reuse bulk POST for edits: creates additional attempts rather than correcting identified records.
- Return only leaderboard best attempts or sort all disciplines by one raw score: loses result history or creates misleading cross-discipline ordering.

## Consequences and trade-offs

- The review UI must handle DUPLICATE and `repeatedOfRowNumber`. A duplicate selected without its canonical row remains skipped.
- CSV/TSV/XLSX and the immediate-import adapter remain supported. This decision does not establish a CSV-only policy or remove the review workflow.
- [ADR-0008](0008-required-import-birth-dates.md) still requires valid past DOB on import. [ADR-0009](0009-name-dob-and-confirmed-identifiers.md) still blocks existing demographic matches without an identifier; repetition detection does not establish database identity.
- Hibernate adds one nullable repeated-row reference column to ImportRow using the current schema-management approach. No database reset is required for this change.
- Result search filters and pagination execute in the database. Performance ordering is meaningful within each definition; frontend result groups should retain discipline/unit context.
- Result deletion on participation removal is destructive and intentional. The API contract now documents its scope and tests that unrelated data survives.
- Frontend integration is separate work; backend delivery does not claim that the review table, age filters or Results Overview UI is complete.

## Verification

- [ImportPreviewValidationIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/importing/ImportPreviewValidationIntegrationTest.java): no-record uploads, empty commits, repeated rows, accented identities, cancellation and commit-time rechecks.
- [AthleteImportUpdateIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java): preserved upserts and identity policy.
- [AgeRangeTest](../../backend/src/test/java/com/flyingangels/backend/athlete/AgeRangeTest.java) and [AthleteAgeFilteringIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/athlete/AthleteAgeFilteringIntegrationTest.java): birthday/leap-day rules and database directory filtering.
- [EventParticipationIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/event/EventParticipationIntegrationTest.java): empty/duplicate/missing assignments, transaction rollback, multiple events and removal scope.
- [ResultEditingIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/event/ResultEditingIntegrationTest.java): existing-attempt corrections and atomic validation failure.
- [ResultsOverviewIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/event/ResultsOverviewIntegrationTest.java): combined filters, meet-start age, time/distance sorting, all attempts and pagination.
- [Execution record](../postman/verification.md): 127 passing JUnit tests; 139 Newman requests / 226 assertions, zero failures, on the uncommitted working tree based on cfe4ca4.
- [API integration guide](../sprint2-api-updates.md): request examples, statuses and frontend responsibilities.

## Supersession

This supplements ADR-0002/0004/0005/0008/0009 and does not supersede their ownership, transaction, measurement or confirmed-identity rules. Record reviewer/date/PR after team ratification.
