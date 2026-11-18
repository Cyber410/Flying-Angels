# ADR-0012: Version schema changes with Flyway and validate with Hibernate

- Recorded: 2026-11-13
- Status: **Implemented; awaiting team ratification**
- Scope: Backend schema initialization and upgrades
- Supersedes: [ADR-0006](0006-hibernate-schema-management.md)
- Basis: The project owner requested explicit migration versions and implementation after discussing database upgrade and rollback behavior.

## Context

Athlete records, accounts, invitation delivery and login-throttling state now survive backend releases. Automatic Hibernate update does not provide a reviewed sequence, applied-file checksums or a controlled transition from an existing schema.

## Decision

Use V1 for the ten existing RDS tables, V2 for the missing Sprint 3 schema, V3 for verified account enrollment and V4 for password recovery. The next schema change becomes V5. Spring Boot runs Flyway before JPA initialization; Hibernate uses `ddl-auto=validate`. Applied/released SQL files are immutable.

On 2026-11-18, before the first RDS Flyway deployment, the owner requested that V1 match the supplied ten-table schema and V2 add its missing functionality. The preceding unreleased layout was reorganized accordingly. Remove the custom Java migration strategy, schema verifier, frozen snapshots and application-specific adoption flag. Use standard Flyway configuration only.

Existing unversioned databases require a backup and manual schema review before a one-time V1 baseline. `baseline-on-migrate=false` is the default; enable it for one reviewed startup, then reset it. Baselining does not prove schema equivalence. Hibernate validates entity compatibility afterward but does not verify every index or constraint. The reviewed ten-table RDS schema needs no separate conversion: its missing changes are in V2 and later migrations. Other starting schemas require review.

## Alternatives and consequences

Keeping Hibernate update would leave schema upgrades implicit. Custom adoption verification adds code and snapshot maintenance; the owner requested a simpler standard-Flyway setup. Destructive recreation would discard retained records.

SQL and entities must be updated together. Test fresh initialization and populated upgrades. A baseline records a starting version, not that V1 SQL executed. Lowering the target does not reverse SQL. MySQL DDL failures and destructive changes require a reviewed recovery or backup restore. Flyway clean is disabled by default.

Verification covers fresh V1–V4 initialization, explicit populated-schema baselining, record preservation, default refusal without history, repeat startup, checksum failure and clean protection. The RDS definitions were supplied and reviewed; no live SQL changes were made by this workspace. Settings and rollout steps are in [Database migrations](../database-migrations.md).
