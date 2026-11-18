# Sprint 3 browser checks

Small Chromium checks, grouped by feature:

- `accounts.spec.ts`: invitation, email verification, athlete route restriction, and password recovery.
- `athlete-profile.spec.ts`: personal best/progression and scoped ranking filters.
- `availability.spec.ts`: athlete reply, stale-response recovery, staff filters and external reply source.
- `meet-export.spec.ts`: registration/results downloads and empty/cancel states.

`helpers.ts` supplies fictional sessions and records. Every API request in the
new tests is intercepted; unmatched requests return a mock 404. These tests
verify frontend flows, not backend authorization or database behavior. No email
is sent and no saved data changes.

From `frontend`:

```bash
npm run test:e2e
npm run test:e2e -- --headed --workers=1
npm run test:e2e -- --debug
```

The test server uses port 5180. Reports go to `playwright-report`; failures retain
screenshots and traces in `test-results`. Both folders are ignored by Git.
