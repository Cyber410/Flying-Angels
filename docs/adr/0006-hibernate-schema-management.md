# ADR-0006: Let Hibernate manage the current database schema

- Recorded: 2026-10-14
- Revised: 2026-10-26
- Status: **Implemented; awaiting team ratification**
- Scope: Backend database initialization
- Basis: The project owner clarified that the initial database is empty and populated from the client CSV, and requested removal of Flyway.
- Approver: Team ratification has not yet been recorded.

## Context

The current sprint creates a fresh database and imports athlete registrations from CSV/TSV/XLSX. Last sprint's development schema is not an older deployed client database that requires conversion. Maintaining a previous-schema creation script, an upgrade script and a migration runner adds unnecessary setup for this workflow.

## Decision

Use Hibernate's `spring.jpa.hibernate.ddl-auto=update` to create missing tables/columns from the current JPA entity mappings. Entity annotations define relationships, unique constraints, measurement precision, column lengths and the note-order index. Remove the Flyway dependencies, baseline settings, SQL migration files and old-schema upgrade test.

The backend startup test verifies that empty MySQL receives all current tables and the notes index. Business integration tests continue to use real disposable MySQL databases. The CSV importer remains an explicit application workflow for saving registration data after startup.

## Alternatives considered

- Keep Flyway versioned migrations: useful for controlled upgrades to persisted schemas, but not required by the current initial-database scope.
- Hibernate create/create-drop: creates a fresh schema but also resets tables across backend lifecycle events, which would unnecessarily erase imported local test data.
- Manual SQL setup: duplicates entity mappings and requires developers to apply setup themselves.

## Consequences and trade-offs

- Developers can start the backend and import the client file without maintaining SQL migration files.
- Backend restarts preserve database rows. The temporary MySQL mount independently clears test data when the database container stops.
- Hibernate update does not infer custom historical-data conversions; this decision reflects the current fresh-database scope.
- Open-in-View remains disabled; services still fetch response data within transactions.

## Implementation and verification evidence

- [application.properties](../../backend/src/main/resources/application.properties)
- [BackendApplicationTests](../../backend/src/test/java/com/flyingangels/backend/BackendApplicationTests.java)
- [ManagementIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java)
- [AthleteNote entity](../../backend/src/main/java/com/flyingangels/backend/note/AthleteNote.java)

## Review and supersession

This unratified record was revised from the earlier Flyway choice at the project owner's request. Record the team's reviewer/date and linked PR when ratified.
