# Postman pack verification

## 2026-10-26: Hibernate-managed schema

The backend passed **92 JUnit tests** after removing Flyway dependencies, baseline configuration, SQL migration files and the legacy-upgrade test. Startup verification checks that Hibernate creates all current tables and the note-order index on empty MySQL, with no migration-history table. Business, import and security tests continue to use disposable databases. The Postman API contracts and 122 functional/8 security request counts are unchanged by this schema-initialization change. Earlier migration-related results below are historical; they do not describe the current setup.

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
