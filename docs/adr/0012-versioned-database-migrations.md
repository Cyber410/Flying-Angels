# ADR-0012: Version schema changes with Flyway and validate with Hibernate

- Recorded: 2026-11-13
- Status: **Implemented; awaiting team ratification**
- Scope: Backend schema initialization and upgrades
- Supersedes: [ADR-0006](0006-hibernate-schema-management.md)
- Basis: The project owner requested explicit migration versions and implementation after discussing database upgrade and rollback behavior.

## Context

Athlete records, accounts, invitation delivery and login-throttling state now survive backend releases. Automatic Hibernate update does not provide a reviewed sequence, applied-file checksums or a controlled transition from an existing schema.

## Decision

Use one initial Flyway SQL migration, V1, containing all 16 current application tables. The next real schema change becomes V2. Spring Boot runs Flyway before JPA initialization; Hibernate uses `ddl-auto=validate`. Applied/released SQL files are immutable.

The initial three-version draft was consolidated at the project owner's request before release. Remove the custom Java migration strategy, schema verifier, frozen snapshots and application-specific adoption flag. Use standard Flyway configuration only.

Existing unversioned databases require a backup and manual schema review before a one-time V1 baseline. `baseline-on-migrate=false` is the default; enable it for one reviewed startup, then reset it. Baselining does not prove schema equivalence. Hibernate validates entity compatibility afterward but does not verify every index or constraint. Older incompatible schemas require a separate reviewed conversion.

## Alternatives and consequences

Keeping Hibernate update would leave schema upgrades implicit. Custom adoption verification adds code and snapshot maintenance; the owner requested a simpler standard-Flyway setup. Destructive recreation would discard retained records.

SQL and entities must be updated together. Test fresh initialization and populated upgrades. A baseline records a starting version, not that V1 SQL executed. Lowering the target does not reverse SQL. MySQL DDL failures and destructive changes require a reviewed recovery or backup restore. Flyway clean is disabled by default.

Verification covers fresh V1 initialization, explicit populated-schema baselining, record preservation, default refusal without history, repeat startup, checksum failure and clean protection. No RDS schema is assumed or changed. Settings and rollout steps are in [Database migrations](../database-migrations.md).
