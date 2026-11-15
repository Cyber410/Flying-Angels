# Database migrations

Flyway changes the MySQL schema using versioned SQL files. Hibernate uses `ddl-auto=validate`: it checks that the resulting schema fits the Java entities and stops startup on a mapping mismatch. Hibernate no longer creates or changes tables automatically.

## Files and startup flow

There is one initial migration: `backend/src/main/resources/db/migration/V1__application_schema.sql`. It contains all 16 current application tables, including invitation email delivery and login-throttling storage. The next actual schema change will become V2.

Spring Boot's Flyway integration runs the migration directly. There are no custom migration configuration/verifier classes, schema snapshots or application-specific adoption flags.

On a fresh database, Flyway creates `flyway_schema_history`, executes V1, and records its checksum and success. Hibernate validates afterward. Later startups validate migration history and run only pending migrations. Regular restarts do not recreate tables or import CSV data.

## Configuration variables

The properties live in `backend/src/main/resources/application.properties`. Standard Spring environment variables can override them. Root Compose forwards the target, baseline version and baseline-on-migrate variables. The Postman override forwards the same standard baseline variables, without custom aliases.

| Property / environment variable | Default | Meaning |
| --- | --- | --- |
| `spring.jpa.hibernate.ddl-auto` / `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Hibernate checks entity-to-schema compatibility. It does not issue table-creation or alteration SQL. Keep this value when using Flyway. |
| `spring.flyway.enabled` / `SPRING_FLYWAY_ENABLED` | `true` | Runs Flyway automatically during application startup. Disabling it does not disable Hibernate validation. |
| `spring.flyway.locations` / `SPRING_FLYWAY_LOCATIONS` | `classpath:db/migration` | Looks for SQL migrations packaged inside the backend JAR. It does not fetch migrations from GitHub. |
| `spring.flyway.target` / `SPRING_FLYWAY_TARGET` | `latest` | Highest version to apply. `latest` means the highest version packaged in this backend image, currently V1. A lower target does not undo applied changes and may leave the schema incompatible with this application's entities. |
| `spring.flyway.baseline-version` / `SPRING_FLYWAY_BASELINE_VERSION` | `1` | Version an existing unversioned database already represents. A baseline at V1 skips V1 SQL; future V2 and later migrations still run. Fresh databases execute V1 normally. |
| `spring.flyway.baseline-on-migrate` / `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `false` | Refuses a non-empty database without history by default. After backup and manual schema review, enable for one startup to record the V1 baseline. It does not prove that the existing schema matches V1. Reset to false afterward. |
| `spring.flyway.clean-disabled` / `SPRING_FLYWAY_CLEAN_DISABLED` | `true` | Blocks Flyway's destructive `clean` operation, which removes schema objects. It does not stop someone with database privileges from manually dropping tables. |
| `spring.flyway.validate-on-migrate` / `SPRING_FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Checks migration history against packaged files before migrating, including checksums of SQL migrations that actually ran. Editing a previously applied SQL migration causes validation failure. |
| `spring.flyway.validate-migration-naming` / `SPRING_FLYWAY_VALIDATE_MIGRATION_NAMING` | `true` | Rejects invalid migration filenames instead of silently overlooking them. Use `V2__clear_description.sql` with two underscores. |
| `spring.flyway.fail-on-missing-locations` / `SPRING_FLYWAY_FAIL_ON_MISSING_LOCATIONS` | `true` | Stops startup if the configured migration resource location is missing. |

Use the defaults for routine local work. There is no HTTP migration endpoint or Postman setting for the database version.

## First run against an existing database

Use the [local backup and restore walkthrough](local-backup-restore.md) to rehearse recovery against a separate MySQL instance before changing a deployed database.

1. Back up the database and verify the backup can be restored into a separate database. Stop application writers during initial baselining. For RDS, take a snapshot and test on a restored copy first.
2. Review the existing schema against V1, including columns, defaults, indexes and relationships. Standard Flyway baselining records your chosen version; it does not verify that all existing objects match the SQL file. Hibernate validation afterward checks entity compatibility, but is not a complete check of indexes or every constraint. If the schema differs, prepare and test a reviewed conversion rather than assigning it a false baseline.
3. For the root local Compose stack with an already-current unversioned schema, start only the backend once with:

   ```sh
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=1 \
     docker compose up -d --build --no-deps backend
   ```

   For the authenticated Postman fixture, add the same two standard environment variables to the existing startup command in its walkthrough. Retain the private outbox key and image/Admin variables. Do not recreate the fixture's MySQL container: its tmpfs data is disposable.
4. Check the result using SQL:

   ```sql
   SELECT installed_rank, version, description, type, success
   FROM flyway_schema_history ORDER BY installed_rank;
   ```

   An existing database has one `BASELINE` row at version 1; a fresh database has one `SQL` row at version 1. A baseline does not execute V1 or rewrite existing records. Check account login and existing athlete/results workflows afterward.
5. Restart the backend with `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` or its default. The history now controls migration execution; baselining is not needed again.

The earlier local three-version experiment was consolidated before release at the project owner's request. The retained `fa-postman` database had a single V3 `BASELINE` row and no executed SQL migrations. After backup, that local baseline metadata was adjusted to version 1; application records were retained. This was a correction to an unreleased local baseline, not a database rollback. Do not rewrite history on other databases, especially ones where versioned SQL actually executed. Review those separately.

## Existing deployment missing the current changes

An older populated database must receive its missing changes before it can truthfully be baselined at V1. The complete V1 creation script is for fresh databases; it is not an upgrade script for an older populated schema.

A draft one-time upgrade is available at [upgrade-existing-to-v1.sql.draft](../deploy/sql/upgrade-existing-to-v1.sql.draft), outside Flyway's migration directory. V1 remains the only automatic migration. The draft is based on the previous committed backend, `671a9ae`, whose ten-table schema was generated using its actual Hibernate mappings in disposable MySQL 8.4. **This is not a live RDS export.**

The reference comparison requires:

| Change | Purpose |
| --- | --- |
| Add nullable `meets.start_time` | Optional meet start time; existing date-only meets retain the default midnight behavior |
| Add nullable `event_results.result_label` | Labels such as Heat 1, Final and Attempt 2 without changing existing results |
| Add `idx_result_ranking` | Supports category/athlete/status/unit/score queries |
| Create `staff_users`, `user_invitations` | Account roles and invitation-based activation |
| Create `availability_requests`, `availability_responses` | Meet-level requests and response history |
| Create `invitation_email_outbox` | Durable invitation-email delivery |
| Create `login_throttle_buckets` | Shared login-failure counters |

The draft does not delete existing records or change recorded performance values. It deliberately does not hide discrepancies behind `IF NOT EXISTS`. Do not apply it to RDS until a schema-only export confirms the starting state, or the draft is revised for the actual differences. MySQL DDL can commit independently; on an error, stop and inspect before rerunning anything.

`DatabaseMigrationIntegrationTest` verifies the draft against the previous-code schema with populated athletes, meets, categories, events, assignments, results and notes. It checks preservation of their existing fields, the new nullable fields/index/tables, and a successful V1 baseline afterward. The schema fixture is test-only. No custom migration/verifier Java classes were reintroduced.

For an AWS-console-only workflow, first collect read-only connection metadata from **RDS → Databases → the instance → Connectivity & security**, including engine/version, status, endpoint, port and public-access setting. Alternatively, in AWS CloudShell in the same Region:

```sh
aws rds describe-db-instances \
  --query 'DBInstances[].{Identifier:DBInstanceIdentifier,Engine:Engine,Version:EngineVersion,Status:DBInstanceStatus,Endpoint:Endpoint.Address,Port:Endpoint.Port,InitialDatabase:DBName,Username:MasterUsername,Public:PubliclyAccessible}' \
  --output table --no-cli-pager
```

This reads instance metadata, not SQL table definitions, and does not expose the database password. The initial database name can be absent or differ from the application's configured database. Use the connection details to choose an existing permitted client path, then export schema-only SQL with `mysqldump --no-data`; do not change networking or start a stopped instance just to collect metadata. Database-password entry stays in the database client's private prompt. The standard RDS MySQL console manages the instance; the console SQL query editor is an Aurora Data API feature.

After the actual schema comparison: test the revised upgrade on a restored copy, validate the upgraded application, back up live RDS, apply the reviewed SQL with writers stopped, then record V1 through the documented one-startup baseline configuration. Automatic baselining is not a substitute for applying missing changes.

References: [RDS connection details](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_GettingStarted.CreatingConnecting.MySQL.html), [read-only instance description](https://docs.aws.amazon.com/cli/latest/reference/rds/describe-db-instances.html), [Aurora query editor](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/query-editor.html).

## Future changes and rollback

For a persisted change, add `V2__clear_description.sql` and update the corresponding entity mapping. Test an empty database and an upgrade from the previous version with representative records. Once released/applied, keep V1 immutable.

`SPRING_FLYWAY_TARGET=1` currently installs the complete schema on a fresh database. When V2 exists, a target of 1 prevents it from running; the application version must still be compatible with V1. Lowering the target after V2 has run does not reverse V2 or restore deleted data. A later correction becomes V3. Recovering lost data may require a tested backup restore. MySQL DDL can commit independently, so inspect failures rather than assuming all statements rolled back.

RDS has not been inspected or changed. If automatic ECS deployment is enabled, complete the backup/restored-copy/schema-review/baseline plan before releasing this change to main. A populated unversioned database is refused by default. Keep future migrations compatible with older backend tasks still running during rolling deployment.

References: [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Flyway baseline safety](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html), [Flyway target](https://documentation.red-gate.com/fd/flyway-target-setting-277579044.html), [versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html).
