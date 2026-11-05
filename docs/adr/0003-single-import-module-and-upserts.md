# ADR-0003: Use one import module with create, update and unchanged behavior

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

**Policy update, 2026-10-15:** The permissive DOB behavior recorded below is historical and is superseded by [ADR-0008](0008-required-import-birth-dates.md). Current imports require a valid past DOB on every row. The single-module, matching, create/update/skip and optional-field preservation decisions remain in effect.

**Identity-policy update, 2026-10-15:** Automatic unique-name/gender matching below is also historical and is superseded by [ADR-0009](0009-name-dob-and-confirmed-identifiers.md). Different known DOBs are distinct people; existing name/DOB matches require a registration identifier before updating or classifying unchanged.

## Context

The club roster contains missing DOB, repeated athletes, changed statuses and performance values. Future registration uploads should enrich existing profiles. Two overlapping import services previously applied different duplicate policies. Missing DOB is not a reason to discard an athlete.

## Decision

Keep all file-import orchestration under importing. Both immediate upload and preview/commit use AthleteImportService, AthleteRegistrationParser, AthleteImportMatcher and ImportRowWriter. Create unmatched athletes; update matched athletes when supplied nonblank information changes; skip UNCHANGED rows. Blank/unusable DOB becomes unknown with a warning and never erases a valid stored DOB. Blank profile fields preserve stored data. Append new historical values rather than deleting earlier results.

## Alternatives considered

- Separate immediate and preview import implementations: preserves independent behavior but duplicates matching and update policy.
- Reject all existing athletes as duplicates: prevents duplicate inserts but loses legitimate registration updates.
- Require DOB or a registration number for every record: simplifies identity but excludes valid club roster entries.
- Replace every profile field from each upload, including blanks: reflects the file literally but can erase known information.

## Consequences and trade-offs

- An existing athlete is not automatically a rejected duplicate. Counts distinguish new athletes, updates and unchanged rows.
- Use a source-scoped external identifier first, then a unique name fallback; several possible matches require review.
- A unique-name fallback is not proof of identity; renamed athletes need a stable identifier to match reliably.
- Repeated rows process in source order, so later nonblank values can update earlier ones.
- Ordinary athlete CRUD retains stricter supplied-DOB validation; import tolerates unusable source dates as unknown.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/importing/AthleteImportService.java](../../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportService.java)
- [backend/src/main/java/com/flyingangels/backend/importing/AthleteImportMatcher.java](../../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportMatcher.java)
- [backend/src/main/java/com/flyingangels/backend/athlete/Athlete.java](../../backend/src/main/java/com/flyingangels/backend/athlete/Athlete.java)
- [backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
