# Endpoint access by role

Authentication must be enabled (`APP_REQUIRE_AUTHENTICATION=true`, the default). The explicitly authentication-disabled CI fixture bypasses these restrictions and must not be used to verify roles.

All accounts use `POST /api/auth/login`. The server loads the role from the account and stores the authenticated identity in the HTTP session. Postman retains the `JSESSIONID` cookie. Changing the request body, sending an athlete ID, or adding a role header does not change the signed-in user's authority.

`SecurityConfig.java` enforces endpoint access centrally. `hasRole("ADMIN")` accepts only Admin; `hasAnyRole("ADMIN", "COACH")` accepts either staff role. Rule order matters: user management and the athlete portal are checked before broader staff rules. Athlete notes have an explicit shared staff rule before Admin-only basic athlete writes. Event and participant notes use the shared staff rule. `MANAGE_NOTES` in login/session responses describes this capability; the server's role checks enforce it.

Admin and Coach can both create/read athlete notes and save/read event and participant notes. Notes are shared between these staff roles. There is no author-only restriction, and no athlete-note edit/delete endpoint. Athlete/Parent accounts cannot read or write private staff notes, including notes on their own athlete.

Athlete/Parent uses an ATHLETE account. Portal routes derive the athlete from that account, and service checks reject unrelated profiles, meet nominations and availability requests. Rankings expose permitted competition information across all meets/events through the portal projection. Admins and Coaches use the staff APIs; their accounts do not gain an athlete identity by calling a portal route.

Coach imports can update permitted performance data, but cannot create athletes or change protected basic athlete information. Import preview and commit both enforce this additional service-level restriction.

## Run the tests in Postman

1. Reimport [the updated security collection](flying-angels-security.postman_collection.json) and select the proxy environment. Use `baseUrl=http://localhost:18080`, `mailpitUrl=http://localhost:18025`, and your Admin `email`/`password`. Keep cookies enabled and clear localhost cookies before starting.
2. Run **Flying Angels - Account roles and athlete permissions** from the beginning, one iteration. It has **438 requests**. It invites and activates isolated Coach and Athlete accounts through local Mailpit before testing their access.
3. The **01 - All endpoints - role access matrix** folder has four role folders. Each tests all **84 method/path combinations**, including the Spring Security logout endpoint: **336 role checks**. Each role has its own authenticated session or an anonymous session, and logs out at the end. Identity assertions confirm that the intended account is actually signed in.
4. Admin and Coach folders also create/read real athlete, event and participant notes. Coach reads the Admin's athlete note; after restoring Admin, Admin reads both notes. Athlete portal responses are checked for note leakage.
5. The collection restores Admin, deactivates its synthetic accounts, removes the synthetic meet/athlete and signs out. Run the separate functional collection from its own Authentication folder for successful CRUD, results, import, export and other business workflows.

The collection now contains only these three top-level folders, in order:

| Folder | Requests | Purpose |
| --- | --- | --- |
| 00 - Account setup and use-case checks | 69 | Sign in, invite accounts and create fixtures |
| 01 - All endpoints - role access matrix | 358 | Four identities, endpoint permissions and shared-note checks |
| 02 - Cleanup and sign out | 11 | Check shared notes, remove fixtures, deactivate accounts and sign out |

The Admin role folder has 93 requests: 84 endpoint probes, three login/CSRF requests and six real note operations. The Coach role folder also has 93 requests. Endpoint count and total request count are different.

Postman's desktop runner runs folders before loose requests at the same level. The previous mixed layout allowed the role folder to run before root-level fixture creation, even when running the parent collection. This caused literal `{{securityAthleteId}}` URLs. All stages now use numbered folders. The collection validates numeric fixture IDs, stops when fixture creation fails, and stops with a named missing-variable error before sending an unresolved ID. See [Postman's documented run order](https://learning.postman.com/v11/docs/tests-and-scripts/running-collections/building-workflows/).

Reimport the updated collection and confirm these three numbered folders are visible. Start a fresh run with every folder selected, in 00 → 01 → 02 order; a saved older run can retain its previous selection/order. Run the complete security collection rather than an isolated role folder: its setup creates the accounts and IDs that those folders need. On a repeated run, clear cookies and start from the beginning again. No manual account switching is needed during the run.

### Interpret the results

- **200/201/204**: successful operation or read, as specified by that request.
- **401**: a protected endpoint was called without an authenticated session.
- **403**: the authenticated role is forbidden, or CSRF protection rejected a write. Role probes send valid CSRF so these failures test authorization independently.
- **400/404 in an allowed-role probe**: intentional invalid body or deliberately nonexistent record. This proves the request passed endpoint authorization and reached validation/resource handling. It does **not** prove a successful business operation. These probes avoid altering real records; functional tests and real shared-note checks cover successful operations separately.

A negative test passes when its expected rejection occurs. Do not treat every red HTTP status as a failed test; read the Postman assertion result.

## Automated regression coverage

[EndpointRoleMatrixIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/auth/EndpointRoleMatrixIntegrationTest.java) uses real logins, the Spring Security filter chain, controllers, services and isolated MySQL. Its [independent expected-access fixture](../../backend/src/test/resources/security/endpoint-role-matrix.json) covers all 84 endpoints under four identities. A route-inventory assertion fails if a controller endpoint is added, removed or renamed without updating the contract. New routes therefore require a deliberate role decision.

The same suite checks all **40 unsafe method/path combinations** without CSRF under all four identities (**160 rejection checks**) and persists all three note types under both staff roles. It checks cross-role note reads, blocked athlete note writes/reads, and private-note exclusion from profile/history/meets. Existing tests cover invitation lifecycle, session invalidation, last-Admin protection, athlete ownership and Coach import restrictions. Browser preflight `OPTIONS` handling remains covered by the separate deployment/CORS collection and CORS backend tests; it is not a business operation in this table.

From `backend`, with Docker running:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home bash mvnw -Dtest=EndpointRoleMatrixIntegrationTest test
```

Verified locally on 2026-11-13 after reproducing the folder-first failure and fixing the layout: the security collection passed 438 ordered collection requests and 512 assertions (plus a variable number of Mailpit polling requests). Across all four collections, 711 collection requests and 1,024 assertions passed. The full backend suite passed 367 tests with two existing skips; its 11 new test cases include the 336 role checks and 160 missing-CSRF checks described above.

The other three collections also use numbered stages, validate generated fixture values and stop missing-setup requests before HTTP. See the [run order and verification totals](README.md#ordering-and-setup-checks-for-every-collection).

A deliberate Admin-folder-only run was verified to stop after its three authentication requests, with a missing-setup failure and no note HTTP request sent. Ordering and fixture guards remain inside the collection JSON; no separate Python collection-checking script is required to run the collections.

## Complete endpoint matrix

“Yes” means the role may reach that endpoint. Payload validation, record existence, ownership and business rules still apply. Public login/activation routes still require valid credentials/tokens; public state-changing routes also require CSRF. Anonymous logout clears an anonymous session and grants no access.

| Method | Endpoint | Admin | Coach | Athlete/Parent | Anonymous |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/age-groups` | Yes | Yes | — | — |
| GET | `/api/athletes` | Yes | Yes | — | — |
| POST | `/api/athletes` | Yes | — | — | — |
| PUT | `/api/athletes/archive` | Yes | — | — | — |
| POST | `/api/athletes/import` | Yes | Yes | — | — |
| POST | `/api/athletes/import/preview` | Yes | Yes | — | — |
| GET | `/api/athletes/import/{id}` | Yes | Yes | — | — |
| POST | `/api/athletes/import/{id}/commit` | Yes | Yes | — | — |
| DELETE | `/api/athletes/{id}` | Yes | — | — | — |
| GET | `/api/athletes/{id}` | Yes | Yes | — | — |
| PUT | `/api/athletes/{id}` | Yes | — | — | — |
| GET | `/api/athletes/{id}/history` | Yes | Yes | — | — |
| GET | `/api/athletes/{id}/notes` | Yes | Yes | — | — |
| POST | `/api/athletes/{id}/notes` | Yes | Yes | — | — |
| POST | `/api/auth/invitations/accept` | Yes | Yes | Yes | Yes |
| POST | `/api/auth/invitations/validate` | Yes | Yes | Yes | Yes |
| POST | `/api/auth/login` | Yes | Yes | Yes | Yes |
| GET | `/api/auth/me` | Yes | Yes | Yes | Yes |
| GET | `/api/availability` | Yes | Yes | — | — |
| GET | `/api/availability/{id}` | Yes | Yes | — | — |
| PUT | `/api/availability/{id}/response` | Yes | Yes | — | — |
| GET | `/api/availability/{id}/responses` | Yes | Yes | — | — |
| GET | `/api/csrf` | Yes | Yes | Yes | Yes |
| GET | `/api/event-categories` | Yes | Yes | — | — |
| POST | `/api/event-categories` | Yes | Yes | — | — |
| GET | `/api/events` | Yes | Yes | — | — |
| POST | `/api/events` | Yes | Yes | — | — |
| DELETE | `/api/events/{id}` | Yes | Yes | — | — |
| GET | `/api/events/{id}` | Yes | Yes | — | — |
| PUT | `/api/events/{id}` | Yes | Yes | — | — |
| GET | `/api/events/{id}/leaderboard` | Yes | Yes | — | — |
| GET | `/api/events/{id}/note` | Yes | Yes | — | — |
| PUT | `/api/events/{id}/note` | Yes | Yes | — | — |
| POST | `/api/events/{id}/note/migrate-browser-data` | Yes | Yes | — | — |
| GET | `/api/events/{id}/participants` | Yes | Yes | — | — |
| POST | `/api/events/{id}/participants` | Yes | Yes | — | — |
| POST | `/api/events/{id}/participants/bulk` | Yes | Yes | — | — |
| DELETE | `/api/events/{id}/participants/{athleteId}` | Yes | Yes | — | — |
| POST | `/api/events/{id}/participants/{athleteId}/migrate-browser-data` | Yes | Yes | — | — |
| GET | `/api/events/{id}/participants/{athleteId}/note` | Yes | Yes | — | — |
| PUT | `/api/events/{id}/participants/{athleteId}/note` | Yes | Yes | — | — |
| PUT | `/api/events/{id}/participants/{athleteId}/result` | Yes | Yes | — | — |
| GET | `/api/events/{id}/results` | Yes | Yes | — | — |
| POST | `/api/events/{id}/results` | Yes | Yes | — | — |
| POST | `/api/events/{id}/results/bulk` | Yes | Yes | — | — |
| PUT | `/api/events/{id}/results/bulk` | Yes | Yes | — | — |
| DELETE | `/api/events/{id}/results/{resultId}` | Yes | Yes | — | — |
| PUT | `/api/events/{id}/results/{resultId}` | Yes | Yes | — | — |
| GET | `/api/meets` | Yes | Yes | — | — |
| POST | `/api/meets` | Yes | — | — | — |
| DELETE | `/api/meets/{id}` | Yes | — | — | — |
| GET | `/api/meets/{id}` | Yes | Yes | — | — |
| PUT | `/api/meets/{id}` | Yes | — | — | — |
| GET | `/api/meets/{id}/availability` | Yes | Yes | — | — |
| GET | `/api/meets/{id}/events` | Yes | Yes | — | — |
| POST | `/api/meets/{id}/events/bulk` | Yes | Yes | — | — |
| POST | `/api/meets/{id}/events/copy` | Yes | Yes | — | — |
| GET | `/api/meets/{id}/export` | Yes | Yes | — | — |
| GET | `/api/meets/{id}/exports` | Yes | Yes | — | — |
| GET | `/api/meets/{meetId}/events/{eventId}` | Yes | Yes | — | — |
| GET | `/api/portal/age-groups` | — | — | Yes | — |
| GET | `/api/portal/availability` | — | — | Yes | — |
| GET | `/api/portal/availability/{id}` | — | — | Yes | — |
| PUT | `/api/portal/availability/{id}/response` | — | — | Yes | — |
| GET | `/api/portal/history` | — | — | Yes | — |
| GET | `/api/portal/meets` | — | — | Yes | — |
| GET | `/api/portal/meets/{id}` | — | — | Yes | — |
| GET | `/api/portal/profile` | — | — | Yes | — |
| GET | `/api/portal/rankings` | — | — | Yes | — |
| GET | `/api/portal/rankings/categories` | — | — | Yes | — |
| GET | `/api/portal/rankings/events` | — | — | Yes | — |
| GET | `/api/portal/rankings/meets` | — | — | Yes | — |
| GET | `/api/rankings` | Yes | Yes | — | — |
| GET | `/api/rankings/categories` | Yes | Yes | — | — |
| GET | `/api/rankings/events` | Yes | Yes | — | — |
| GET | `/api/rankings/meets` | Yes | Yes | — | — |
| GET | `/api/results` | Yes | Yes | — | — |
| GET | `/api/users` | Yes | — | — | — |
| POST | `/api/users` | Yes | — | — | — |
| POST | `/api/users/invitations` | Yes | — | — | — |
| PATCH | `/api/users/{id}` | Yes | — | — | — |
| POST | `/api/users/{id}/invitation/resend` | Yes | — | — | — |
| GET | `/api/users/{id}/invitation/delivery` | Yes | — | — | — |
| POST | `/api/auth/logout` | Yes | Yes | Yes | Yes |
