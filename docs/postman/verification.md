# Postman pack verification

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

This evidence supplements the previously passing 79-test backend suite. HTTP requests do not replace migration tests, injected-write-failure rollback tests, or separately provisioned COACH/VIEWER role checks. No performance benchmark, full accessibility assessment, load test or production-security certification is claimed.

When the backend changes, rerun both collections in their appropriate modes, save the new results, and update this record with the tested commit/profile and any failures. Architecture Decision Records require team ratification separately; passing requests do not approve a design decision.
