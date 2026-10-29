# ADR-0008: Require a valid birth date on every imported athlete row

- Recorded: 2026-10-15
- Status: **Implemented; awaiting team ratification**
- Scope: Backend CSV/TSV/XLSX import validation
- Supersedes: The permissive DOB policy in [ADR-0003](0003-single-import-module-and-upserts.md); its other import decisions remain in effect.
- Basis: The project owner requested that athletes without DOB must no longer be imported.
- Approver: Team ratification has not yet been recorded.

## Context

The earlier importer accepted missing/unusable DOB as unknown so incomplete club records could be imported. The updated requirement is to prevent imports without a valid birth date. The rule must apply consistently to preview/commit, immediate uploads, updates to existing athletes and eligible previews saved before the policy change.

## Decision

Every incoming import row must contain first name, last name and a valid birth date strictly before today. ISO text dates and typed XLSX date cells are supported. Missing/unusable dates, Excel serial numbers in text CSV, and today/future dates make the row INVALID with an explanation. A matched athlete's stored DOB does not exempt a row from the incoming-DOB requirement.

Validate in the parser and defensively in the matcher. Commit reparses the stored source and rechecks eligible rows before writing, so a previously eligible no-DOB preview cannot bypass the new rule. Selected invalid rows contribute to failed; other valid selected rows can succeed. Existing athletes are not deleted or modified because their old data lacks DOB. A valid later upload may fill a legacy profile's missing DOB. Ordinary manual CRUD remains compatible with optional DOB; this decision concerns imports.

Preserve the other import rules: create unmatched athletes, update supplied changes, skip unchanged records, preserve blank optional fields, use source-scoped external IDs where available, and append historical values. Race measurements use seconds and jump/throw measurements use metres; raw unrecognized historical values remain separate from comparable numeric scores.

## Alternatives considered

- Require DOB only for new athletes: permits incomplete update rows, contrary to the requested import rule.
- Substitute a placeholder or guessed DOB: creates inaccurate athlete information.
- Reject the entire file when one DOB is missing: unnecessarily prevents other valid athletes from importing.
- Make the database DOB column non-null globally: affects legacy/manual records beyond this import requirement.

## Consequences and trade-offs

- Some rows from the current client roster will now be INVALID until DOB is supplied.
- Administrators can still review the entire file and commit other valid rows.
- Correcting a source file requires a new preview; successful old rows are not replayed.
- Postman fixtures and tests must distinguish missing-DOB rejection from valid registration updates.
- No schema migration or deletion of existing profiles is required.

## Implementation and verification evidence

- [AthleteRegistrationParser](../../backend/src/main/java/com/flyingangels/backend/importing/AthleteRegistrationParser.java)
- [AthleteImportMatcher](../../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportMatcher.java)
- [AthleteImportUpdateIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java)
- [AthleteRegistrationParserSpreadsheetTest](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteRegistrationParserSpreadsheetTest.java)
- [Backend workflows](../backend-workflows.md)

## Review and supersession

Record the team's reviewer/date and linked PR when ratified. A later policy change should link a replacement ADR and preserve this decision's history.
