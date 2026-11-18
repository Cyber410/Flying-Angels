# Database migrations

Flyway changes the MySQL schema using versioned SQL files. Hibernate uses `ddl-auto=validate`: it checks that the resulting schema fits the Java entities and stops startup on a mapping mismatch. Hibernate no longer creates or changes tables automatically.

## Files and startup flow

The migrations follow the existing AWS database schema:

| Version | Purpose |
| --- | --- |
| V1__application_schema.sql | The ten existing tables supplied from RDS on 2026-11-18 |
| V2__sprint3_schema.sql | Six tables for security, availability, invitation delivery and login throttling; optional meet start time, result labels and the ranking index |
| V3__verified_account_enrollment.sql | Pending account invitations and the corresponding email-outbox changes |
| V4__password_recovery.sql | Password-reset challenges and durable email-delivery state |

The current schema has 18 application tables plus `flyway_schema_history`. The next schema change becomes V5; applied/released migrations must remain immutable. This layout was revised before the first Flyway deployment to RDS, at the project owner's request. Its supplied table list contains no Flyway history.

Spring Boot runs Flyway directly; there are no custom migration configuration/verifier classes or separate adoption SQL scripts. A fresh database executes V1 through V4. The reviewed existing ten-table database is explicitly baselined at version 1 for one startup, then Flyway executes V2 through V4 itself. Hibernate validates the final schema afterward. Regular restarts validate history and run only pending migrations.

## Configuration variables

The properties live in `backend/src/main/resources/application.properties`. Standard Spring environment variables can override them. Root Compose forwards the target, baseline version and baseline-on-migrate variables. The Postman override forwards the same standard baseline variables, without custom aliases.

| Property / environment variable | Default | Meaning |
| --- | --- | --- |
| `spring.jpa.hibernate.ddl-auto` / `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Hibernate checks entity-to-schema compatibility. It does not issue table-creation or alteration SQL. Keep this value when using Flyway. |
| `spring.flyway.enabled` / `SPRING_FLYWAY_ENABLED` | `true` | Runs Flyway automatically during application startup. Disabling it does not disable Hibernate validation. |
| `spring.flyway.locations` / `SPRING_FLYWAY_LOCATIONS` | `classpath:db/migration` | Looks for SQL migrations packaged inside the backend JAR. It does not fetch migrations from GitHub. |
| `spring.flyway.target` / `SPRING_FLYWAY_TARGET` | `latest` | Highest version to apply. `latest` currently applies through V4. This backend requires the V4 schema; a lower target does not undo applied changes and can leave the schema incompatible. |
| `spring.flyway.baseline-version` / `SPRING_FLYWAY_BASELINE_VERSION` | `1` | Version an existing unversioned database already represents. A baseline at V1 skips V1 SQL, then V2, V3 and V4 run. Do not baseline this database at V4: that would skip required changes. Fresh databases execute all migrations normally. |
| `spring.flyway.baseline-on-migrate` / `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `false` | Refuses a non-empty database without history by default. After backup and manual schema review, enable for one startup to record the V1 baseline. It does not prove that the existing schema matches V1. Reset to false afterward. |
| `spring.flyway.clean-disabled` / `SPRING_FLYWAY_CLEAN_DISABLED` | `true` | Blocks Flyway's destructive `clean` operation, which removes schema objects. It does not stop someone with database privileges from manually dropping tables. |
| `spring.flyway.validate-on-migrate` / `SPRING_FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Checks migration history against packaged files before migrating, including checksums of SQL migrations that actually ran. Editing a previously applied SQL migration causes validation failure. |
| `spring.flyway.validate-migration-naming` / `SPRING_FLYWAY_VALIDATE_MIGRATION_NAMING` | `true` | Rejects invalid migration filenames instead of silently overlooking them. Use `V2__clear_description.sql` with two underscores. |
| `spring.flyway.fail-on-missing-locations` / `SPRING_FLYWAY_FAIL_ON_MISSING_LOCATIONS` | `true` | Stops startup if the configured migration resource location is missing. |

Use the defaults for routine local work. There is no HTTP migration endpoint or Postman setting for the database version.

## First startup against the reviewed RDS database

The owner supplied `SHOW TABLES` and all ten `SHOW CREATE TABLE` definitions from `flying-angels-db` / `flying_angels` on 2026-11-18. V1 contains those exact definitions, in dependency order. RDS is private in `vpc-0f3b48478876eca97`; a CloudShell VPC environment already connected over TLS. The owner reports no application records and has a recovery snapshot. No live database writes have been made by this workspace.

Tables without rows still form a non-empty **schema**. Flyway therefore requires the explicit one-time baseline even though the application data is empty. The baseline tells Flyway the existing tables represent V1; V1 SQL is skipped so it does not try to recreate them. There is no manual conversion script to run.

1. Keep the ECS service desired count at zero while merging/building the revised backend image. Retain the available snapshot. Do not run the superseded `upgrade-rds-existing-to-v1.sql` script.
2. In the **backend** container's task definition, add these plain environment values for the first updated startup:

   ```text
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
   SPRING_FLYWAY_BASELINE_VERSION=1
   ```

   Keep `SPRING_FLYWAY_TARGET=latest` (or its default), Hibernate `ddl-auto=validate`, and all existing secrets unchanged. Attach the revision to the service; registering a revision alone does not update the service.
3. Start the updated backend only after the new image and deployment configuration are ready. Flyway records BASELINE 1 and executes SQL migrations 2, 3 and 4. V2 adds the missing tables/columns/index; V3 and V4 add enrollment and recovery. The empty `staff_users` table then allows the configured first Admin to be bootstrapped.
4. Check CloudWatch for successful migration/startup. In the private MySQL client, run:

   ```sql
   SELECT installed_rank, version, description, type, success
   FROM flyway_schema_history ORDER BY installed_rank;

   SHOW TABLES;
   ```

   Expect BASELINE 1 followed by successful SQL 2, 3 and 4, with eighteen application tables plus history. Fresh databases instead have SQL rows for all four versions.
5. Set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` or remove the override in the next revision. After the history exists, routine startups use it and only apply pending migrations. Check Admin login and the main application flows.

For other existing databases, review their actual schema before baselining. `baseline-on-migrate=true` is not a schema checker. Do not use this baseline for a database already containing a different subset of Sprint 3 changes.

## Verification and existing local databases

`DatabaseMigrationIntegrationTest` verifies fresh initialization, direct adoption of the ten-table schema, upgrades from versioned V1 and V2, preservation of existing fields in all ten legacy tables, repeat startup, default refusal without history, checksum rejection and disabled clean. The historical test schema has equivalent columns/constraints in a different column order; it is also adopted directly without a conversion script. `BackendApplicationTests` verifies that all four migrations run and Hibernate accepts the final schema.

Local databases that ran the earlier unreleased migration layout have different V1/V2 checksums and history. They are not compatible with this relabeling merely because they already have the final tables. Do not run Flyway repair, enable a baseline over their existing history, or drop them automatically. Back up any needed data and plan an explicit transition. Only intentionally disposable test databases can be recreated. The retained Postman/restore databases were not changed by this work.

## Future changes and rollback

For the next schema change, add `V5__clear_description.sql` and update entity mappings together. Test fresh initialization and upgrades from the previous version with representative records. Keep V1 through V4 immutable after this layout is released.

`SPRING_FLYWAY_TARGET=1` installs only the ten legacy tables on a fresh database, which is insufficient for the current backend. Leave the target at `latest`. Lowering the target after V4 has run does not reverse migrations or remove tables. A later correction becomes a new forward migration. MySQL DDL can commit independently; inspect failures before retrying. Recovering lost data may require a verified backup restore.

References: [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Flyway baseline](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html), [Flyway target](https://documentation.red-gate.com/fd/flyway-target-setting-277579044.html), [versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html).
