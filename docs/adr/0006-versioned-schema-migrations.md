# ADR-0006: Manage schema evolution with Flyway and Hibernate validation

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

The backend now needs a data-aware transition from placeholder results to participation, date-range backfills, new uniqueness constraints and persisted imports. Automatic schema updates alone do not document or safely reproduce those transformations.

## Decision

Use Flyway SQL migrations. V1 creates the original schema for fresh databases; a compatible existing original schema can be baselined at V1. V2 adds/backfills the current model and removes the old result uniqueness. Set Hibernate ddl-auto=validate and disable Open-in-View; use transactions/entity graphs for response data.

## Alternatives considered

- Continue Hibernate ddl-auto=update: convenient development startup but no explicit data-upgrade sequence.
- Manual undocumented database edits: quick locally but difficult to reproduce and review.
- Recreate the database for every change: loses existing athlete history.

## Consequences and trade-offs

- Migrations are reviewable and tested for both fresh and original-schema upgrades.
- Do not rewrite migrations already applied in a shared environment; add new versions.
- Baselining assumes the old schema is compatible and is not a schema-repair tool.
- Existing duplicate meet/category instances must be resolved before V2.
- MySQL DDL is not fully transactional; failed upgrades require inspection and deliberate repair.
- Disabling Open-in-View requires fetching/mapping discipline; broad collection graphs can still be expensive.

## Implementation and verification evidence

- [backend/src/main/resources/application.properties](../../backend/src/main/resources/application.properties)
- [backend/src/main/resources/db/migration/V1__legacy_schema.sql](../../backend/src/main/resources/db/migration/V1__legacy_schema.sql)
- [backend/src/main/resources/db/migration/V2__participation_notes_imports_and_dates.sql](../../backend/src/main/resources/db/migration/V2__participation_notes_imports_and_dates.sql)
- [backend/src/test/java/com/flyingangels/backend/LegacyMigrationTest.java](../../backend/src/test/java/com/flyingangels/backend/LegacyMigrationTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
