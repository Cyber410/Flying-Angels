# Postman pack verification

## 2026-11-13: Draft existing-database upgrade

Generated the previous committed backend's actual ten-table Hibernate schema from `671a9ae` in disposable MySQL 8.4. Compared it with V1 and prepared `deploy/sql/upgrade-existing-to-v1.sql.draft`: six tables, two nullable columns and one rankings index. The script is outside automatic migration resources and is not approved for live RDS until its real schema export is compared.

The targeted `DatabaseMigrationIntegrationTest` run passed all **5 tests**, including an added populated previous-schema upgrade case. Existing athlete, meet, category, event, assignment, result and note fields survived the SQL upgrade and V1 baseline. Log: `/tmp/fa-upgrade-to-v1-tests.log`. The earlier full-suite run predates this added test; the full suite and Postman collections were not rerun for this draft-only change.

No AWS credentials, endpoint or schema export were available in the workspace; no RDS queries or changes were performed. The current fresh-database V1 and local running stacks remain unchanged. See [Existing deployment upgrade](../database-migrations.md#existing-deployment-missing-the-current-changes).

## 2026-11-13: Local backup and restore rehearsal

Restored the existing fa-postman database into the separate fa-restore-rehearsal project. The complete schema/data exports matched byte-for-byte before backend startup. Flyway retained V1 history and Hibernate validation passed. API checks confirmed Admin login, 9 athletes, 9 performances, 2 notes and 37 restored accounts.

The source had 28 SENT emails and no pending messages. A synthetic invitation created only in the restored copy was backed up and restored with its encrypted payload intact. With the retained outbox key, the worker delivered it once to a separate local Mailpit inbox and its activation token validated. Delivery was disabled again afterward. No synthetic restore account exists in the source; its record counts are unchanged.

Both local stacks remain healthy for inspection. Restored API: http://127.0.0.1:18081; restored Mailpit: http://127.0.0.1:18026. Original API remains http://localhost:18080. No AWS or frontend changes. Steps and disposable Compose configuration are in [Local backup and restore](../local-backup-restore.md).

## 2026-11-13: Consolidated V1 with standard Flyway

The project owner requested one initial migration and removal of the custom migration strategy and schema verifier. V1 now contains all 16 application tables; there are no schema snapshots or custom adoption flags. This supersedes the three-version draft below.

| Check | Result |
| --- | --- |
| Clean backend JUnit build | 373 tests: 371 passed, 2 existing skips, no failures or errors |
| Migration integration tests, included above | 4 passed: fresh V1, explicit populated-schema baseline/restart, refusal without a baseline, checksum validation and disabled clean |
| All four authenticated Postman collections | 1,024 assertions, no failures |
| Backend image | Built successfully using the existing multi-stage Dockerfile |
| Existing local database | Backed up; its only history row was an experimental V3 BASELINE, adjusted to V1; before/after application-data exports were byte-for-byte identical |
| Local stack | Healthy; standard baseline-on-migrate is false and baseline version is 1 |

No executed SQL migration history was rewritten, no application tables were recreated, and no RDS changes were made. Frontend source is unchanged. Logs: `/tmp/flying-angels-single-v1-tests.log`, `/tmp/flying-angels-single-v1-build.log` and `/tmp/fa-single-v1-newman.log`. See [Database migrations](../database-migrations.md).

## 2026-11-13: Earlier three-version draft (superseded)

Tested the uncommitted backend working tree locally. No RDS resources or external email providers were used; frontend source was unchanged.

| Check | Result |
| --- | --- |
| Full backend JUnit suite | 384 tests: 382 passed, 2 existing skips, no failures or errors |
| Migration integration tests, included above | 15 passed: fresh initialization, populated V1/V2/V3 adoption, record preservation, drift rejection, repeat startup, targets, checksum validation and disabled clean |
| All four authenticated Postman collections | 711 ordered requests plus polling subrequests; 1,024 assertions, no failures |
| Existing deployed-stack Python tests | 6 passed against a separate fresh Docker stack on localhost:18081 |
| Backend image | Built successfully with the existing multi-stage Dockerfile |
| Existing `fa-postman` database | Private backup taken; verified V3 baseline recorded; before/after data-only SQL exports were byte-for-byte identical |
| Normal restart | Adoption reset to false; existing local stack healthy on localhost:18080 |
| Fresh Docker database | History contains successful SQL versions 1, 2 and 3; Hibernate validation passed |

The extra fresh CI stack was removed after verification. The existing authenticated Postman stack and its MySQL container were retained. The migration strategy now supersedes the historical Hibernate-update entries below; see [Database migrations](../database-migrations.md) for settings and rollout steps.

Local execution logs are `/tmp/flying-angels-flyway-full-tests.log`, `/tmp/flying-angels-flyway-migration-tests.log`, `/tmp/fa-flyway-newman.log`, and `/tmp/fa-flyway-deployed-stack-tests.log`. They describe this working tree, not a published release or live AWS deployment.

## 2026-10-30: Sprint 2 import, participation and result contracts

Tested the **uncommitted working tree on `fix/meet-result`, based on `cfe4ca4`**. This record covers the implementation described in [Sprint 2 API contracts](../sprint2-api-updates.md) and [ADR-0010](../adr/0010-sprint2-import-and-result-contracts.md); it does not claim that a new commit or deployment has been published.

| Check | Executed | Failures / errors / skipped |
| --- | ---: | --- |
| Full JUnit suite with disposable MySQL | 127 tests | 0 / 0 / 0 |
| Newman 6 functional collection, anonymous default profile | 139 requests; 226 assertions | 0 request or assertion failures |
| Collection source/fixture validator | 41 controller method/path combinations; 139 functional and 8 security requests | Passed |
| Backend package and runtime image | Current JAR and local `sprint2-fixes` image | Passed |

JUnit was run with `bash mvnw -o -B -Dapi.version=1.44 clean test` in `backend`. New tests cover import no-record/selection validation, repeated registrations, commit-time identity checks, exact age filtering, participation assignment/removal scope, atomic result corrections and Results Overview. The existing suite also continues to cover row-write failure recovery and secure-mode behavior.

Newman used `deploy/compose.ci.yml` with isolated project `fa-sprint2-newman-20261030`, a fresh MySQL 8.4 **tmpfs** database, the newly packaged backend and synthetic fixture uploads. It executed the entire ordered functional collection against `http://backend:8080`; the test stack was removed afterward. It did not reuse or reset the developer's usual database. Local run evidence is in `/tmp/fa-sprint2-full-tests.log`, `/tmp/fa-sprint2-newman.log` and `/tmp/fa-sprint2-newman-reports/functional.json` / `functional.xml`; those temporary paths are not repository artifacts.

The separate security Postman collection was **not rerun** in this update. The default-profile Newman run is not a production-security verification or a load/performance benchmark. Earlier execution records below remain historical.

## 2026-10-26: Hibernate-managed schema

The backend passed **92 JUnit tests** after removing Flyway dependencies, baseline configuration, SQL migration files and the legacy-upgrade test. Startup verification checks that Hibernate creates all current tables and the note-order index on empty MySQL, with no migration-history table. Business, import and security tests continue to use disposable databases. The Postman API contracts and 122 functional/8 security request counts are unchanged by this schema-initialization change. This 2026-10-26 setup is historical. It is superseded by the 2026-11-13 Flyway migration implementation in [Database migrations](../database-migrations.md).

## 2026-10-15: Name/DOB and identifier-confirmation update

The earlier identity-policy backend suite passed **93 JUnit tests** with disposable MySQL. New tests verify separate people for different DOBs (one or multiple existing same-name candidates), review for even one exact name/DOB match without an ID, no gender bypass, exact-match identifier attachment, legacy unknown-DOB review, and row rechecks during commit. The current functional collection has **122 requests and 200 scripted assertions**, including new-person, blocked-match and identifier-confirmed-update fixtures. These revised HTTP scripts have been source/fixture validated; they have not been re-executed with Newman in this update. The security collection remains 8 requests/9 assertions.

## 2026-10-15: Required DOB and event measurement update

The earlier required-DOB update passed **87 JUnit tests** with disposable MySQL, including required-DOB enforcement in preview/commit/immediate workflows, revalidation of legacy eligible previews, preserved XLSX times, and athlete-profile retrieval of normalized race/jump/throw measurements. Its Postman collection/fixtures added missing/invalid/future DOB rejection and checks for 800m seconds, Long Jump metres and Shot Put metres. The source-route/fixture validator passes for all 39 endpoints.

The Newman results below are historical evidence from before this policy change; the revised collection has not been re-executed with Newman in this update. Re-import the revised collection and use the revised fixtures when running it manually.

## 2026-10-14: Earlier HTTP verification

Recorded: **2026-10-14**, America/Toronto.

The collections were executed with Newman 6 against the packaged current backend and a **disposable MySQL 8.4 container**, separate from the project's Compose database. Uploaded files were the bundled synthetic fixtures. Credentials used for the security check were temporary test credentials and are not stored in the collection/environment exports.

| Collection | Requests executed | Assertions executed | Failed requests | Failed assertions |
| --- | ---: | ---: | ---: | ---: |
| Functional backend collection | 115 | 187 | 0 | 0 |
| Secure-mode collection | 8 | 9 | 0 | 0 |
| Total | 123 | 196 | 0 | 0 |

The functional run used anonymous development mode. The security run used the same application with `app.security.require-authentication=true` and a synthetic bootstrap ADMIN account. It checked the actual HTTP Basic/CSRF filter behavior; it was not a production deployment test.

Observed detail: Basic-auth write requests without a valid CSRF token returned **401** in the live configuration, while valid token/cookie writes succeeded. Pre-authenticated MockMvc security tests exercise a different request context and return **403** for their CSRF/role-denial scenarios. The collection and guide document this distinction rather than asserting that every security rejection must be 403.

The source-route coverage validator confirms that the functional collection represents all **39 controller method/path combinations**. It also validates request JSON templates and fixture paths:

```sh
python3 docs/postman/validate.py
```

This evidence supplements the previously passing 79-test backend suite. HTTP requests do not replace schema-startup tests, injected-write-failure rollback tests, or separately provisioned COACH/VIEWER role checks. No performance benchmark, full accessibility assessment, load test or production-security certification is claimed.

When the backend changes, rerun both collections in their appropriate modes, save the new results, and update this record with the tested commit/profile and any failures. Architecture Decision Records require team ratification separately; passing requests do not approve a design decision.
