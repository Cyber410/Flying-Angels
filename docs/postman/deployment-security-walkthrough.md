# Test deployment, CORS, CSRF and backend workflows in Postman

This walkthrough uses the production multi-stage images behind Nginx, a disposable MySQL database, authentication and CSRF enabled, and Mailpit for local email capture. It does not start AWS resources or send external emails. No new frontend functionality is needed.

## 1. Start the isolated local stack

Start Docker Desktop. In a terminal, change into the repository:

```sh
cd /Users/jitender/College/Sem6/Capstone/Flying-Angels

export BACKEND_IMAGE=flying-angels-backend:postman
export FRONTEND_IMAGE=flying-angels-frontend:postman
export POSTMAN_ADMIN_EMAIL=postman-admin@example.test
export POSTMAN_ADMIN_PASSWORD='Postman-only-password-123!'
export CI_HTTP_PORT=18080
export POSTMAN_MAILPIT_PORT=18025

# Generate a private local key once; retain this ignored file across backend restarts.
if [ ! -f .env.postman.local ]; then
  (umask 077; printf 'POSTMAN_MAIL_OUTBOX_KEY=%s\n' "$(openssl rand -base64 32)" > .env.postman.local)
fi

docker build -f deploy/backend.Dockerfile -t "$BACKEND_IMAGE" .
docker build -f deploy/frontend.Dockerfile -t "$FRONTEND_IMAGE" .

docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml \
  up -d --no-build --wait --wait-timeout 240
```

The password above is synthetic, for this disposable loopback-only test stack. Use a different value if desired, and enter the same value in Postman. Builds need internet access on the first run. Mailpit is pulled automatically if absent. The Postman override is essential: `compose.ci.yml` by itself disables authentication and CSRF and cannot prove these security requirements.

| Address | Use |
| --- | --- |
| `http://localhost:18080` | Nginx entry point; all Postman API requests go through it |
| `http://localhost:18025` | Mailpit inbox and local email API |
| `http://localhost:5173` | Allowed Origin header value; no service needs to run here for the tests |

Only Nginx and the Mailpit UI are published, on loopback. This stack does not publish the backend or MySQL ports. It uses its own Compose project, container names and disposable database, independent of the root development stack.

The override selects the production Spring profile but explicitly permits non-Secure cookies for local HTTP. Real deployments must use HTTPS and Secure cookies. The MySQL fixture contains synthetic credentials and is not production deployment configuration.

Keep the same terminal for later Compose commands so its exported variables remain available. **Stopping or removing the tmpfs MySQL container erases this test database.** Finish the walkthrough before cleanup. Changing bootstrap credentials does not change an Admin already created in a running database.

## 2. Import the Postman files

Use the Postman desktop application, or the web application with its desktop agent.

Import:

1. [proxy.postman_environment.json](proxy.postman_environment.json).
2. [flying-angels-deployment-security.postman_collection.json](flying-angels-deployment-security.postman_collection.json) — 39 focused requests.
3. [flying-angels-security.postman_collection.json](flying-angels-security.postman_collection.json) — 438 account-role/session requests.
4. [flying-angels-invitations.postman_collection.json](flying-angels-invitations.postman_collection.json) — 48 activation/Athlete-account requests.
5. [flying-angels-backend.postman_collection.json](flying-angels-backend.postman_collection.json) — 186 requests including independent Admin authentication.

Select **Flying Angels - Local proxy and security tests** as the active environment. Set its local values:

| Variable | Value |
| --- | --- |
| `baseUrl` | `http://localhost:18080` — no `/api` suffix or trailing slash |
| `mailpitUrl` | `http://localhost:18025` |
| `email` | `postman-admin@example.test` |
| `password` | The value exported as `POSTMAN_ADMIN_PASSWORD` |
| `trustedOrigin` | `http://localhost:5173` |
| `untrustedOrigin` | `https://untrusted.example.test` |
| `ageAsOf` | Your explicit ISO reference date, for example `2026-11-12` |

Keep Authorization set to **No Auth**: authentication uses the session cookie, not Basic Auth or a bearer token. Keep the cookie jar enabled. Before the focused collection, open **Cookies**, select `localhost`, and clear existing cookies for that host. Cookies are scoped to hosts, not ports; other localhost applications may also use `JSESSIONID`. Avoid switching between localhost application sessions while testing, and do not change `localhost` to `127.0.0.1` halfway through a run.

Do not add a collection-wide automatic CSRF script to the focused collection: its deliberate missing-token tests must really omit the token. Do not share/export populated passwords or activation tokens.

## 3. Run the focused 39-request collection

Select **Flying Angels - Deployment, proxy, CORS and CSRF verification**, choose **Run**, select **Functional → Local** if prompted, select every request, use one iteration, and run in the displayed order. Do not provide a Runner data file. Leave **Run collection without using stored cookies** unchecked. Enable **Save cookies after collection run** when continuing to manual requests afterward and **Keep variable values** when you want captured variables retained for inspection. Then select **Start run**. See the [Postman Collection Runner documentation](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs/).

| Folder | What a passing run proves |
| --- | --- |
| 00 — Proxy and anonymous access | Nginx serves HTML, hides its version, redirects `/api` to `/api/`, and the backend requires authentication |
| 01 — CORS | Allowed preflight succeeds; untrusted/lookalike origins and an actual disallowed-origin request receive 403 |
| 02 — Login and CSRF | Missing/incorrect CSRF or the wrong header receives 403; malformed email receives 400; wrong password receives 401; valid login succeeds |
| 03 — Authenticated API | Session reads succeed, JSON errors survive proxying, email validation works, protected writes require CSRF, and a synthetic athlete is created/read/deleted |
| 04 — Logout | Logout requires CSRF, removes the authenticated session, and a CSRF token alone cannot authorize staff writes |
| 05 — Login throttling | Five failures for a run-specific nonexistent email return generic 401; the next returns 429 and Retry-After, even with forged forwarding headers |

Expected 400/401/403/429 responses are successful negative tests when their assertions pass. Judge **Test Results/Runner assertions**, not whether every response is 200. The collection finishes signed out and deletes its synthetic athlete. If interrupted before cleanup, delete that athlete manually after signing in again, or reset only this disposable stack.

The throttle folder assumes the default account limit of five and a 900-second cooldown. It never targets the Admin account for lockout. Java integration tests use a controllable clock to verify recovery without a 15-minute sleep. A successful login resets account failures; an already blocked account must wait for Retry-After even with the correct password.

## 4. Repeat the important requests manually

Create a separate manual collection with No Auth. Use raw JSON bodies where shown. Let Postman retain the `JSESSIONID` cookie; do not paste session IDs into an Authorization header.

### A. Obtain CSRF metadata

Send `GET {{baseUrl}}/api/csrf`.

Expected: **200**, with `token`, `headerName` and `parameterName`. A new session normally supplies a `JSESSIONID` cookie. Add this to **Scripts → Post-response** on this request:

```javascript
pm.test("CSRF metadata returned", () => pm.response.to.have.status(200));
const csrf = pm.response.json();
pm.collectionVariables.set("csrfToken", csrf.token);
pm.collectionVariables.set("csrfHeader", csrf.headerName);
```

The current header name is `X-CSRF-TOKEN`. Use the server-provided name rather than guessing `X-XSRF-TOKEN`.

### B. Sign in

Send `POST {{baseUrl}}/api/auth/login`, Body → raw → JSON:

```json
{"email":"{{email}}","password":"{{password}}"}
```

Add `Content-Type: application/json` and a header whose key is `{{csrfHeader}}` and whose value is `{{csrfToken}}`. Expected: **200**, `user.role` equal to `ADMIN`, and a valid authenticated session cookie.

Repeat with no CSRF header → **403**; with `X-NOT-CSRF-TOKEN` holding the correct token → **403**; with the correct header and an incorrect token → **403**. Fetch fresh CSRF first if you have already logged in successfully. Missing/wrong CSRF is rejected before credentials or email format are checked.

After successful login, **send the CSRF GET again**. Login rotates the session ID and clears the prior token. Then `GET {{baseUrl}}/api/auth/me` should show your Admin, and `GET {{baseUrl}}/api/athletes` should return **200**.

### C. Test CORS preflights

Send `OPTIONS {{baseUrl}}/api/athletes` with these headers and no body:

```http
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type, X-CSRF-TOKEN
```

Expected: **200**, `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Credentials: true`, allowed method POST, and the requested headers allowed. Preflights do not require login or CSRF.

Change only Origin to `https://untrusted.example.test` → **403**, without an allow-origin grant. `http://127.0.0.1:5173`, `https://localhost:5173`, and a different port are also different origins unless explicitly configured.

For an actual-request check, send a signed-in `GET /api/athletes` with the allowed Origin → **200** and CORS response headers. Change Origin to the untrusted value → **403**.

Postman does not enforce browser CORS or SameSite behavior. These tests verify the server's response to the headers you provide. A GET without Origin can succeed in Postman even when no origins are allowed; that does not mean CORS is disabled. A browser test is still needed for browser-specific cookie/CORS behavior.

### D. Test protected writes

After login and CSRF refresh, send `POST {{baseUrl}}/api/athletes`:

```json
{
  "firstName": "Postman",
  "lastName": "Manual Security Test",
  "dateOfBirth": "2010-01-01",
  "gender": "Female",
  "events": []
}
```

With session cookie and correct CSRF header → **201**. Record `id`, then delete it using `DELETE {{baseUrl}}/api/athletes/{id}` with the same current CSRF header → **204**. With the cookie but no token → **403**; an incorrect token → **403**. Use a unique athlete name if repeating the create, because duplicate identities can return **409**.

### E. Test email validation separately from delivery

While signed in as Admin, send `POST {{baseUrl}}/api/users/invitations` with the current CSRF header:

```json
{"email":"not-an-email","name":"Email Test","role":"COACH"}
```

Expected: **400**, code `VALIDATION_ERROR`, with `email` in the message. Repeat with `email: ""` or an address exceeding 254 characters → **400**. A plausible email with incorrect login credentials instead returns **401**. A duplicate account email returns **409**.

To test delivery, replace the email with a unique synthetic address such as `postman-coach-001@example.test`. Expected: **201**, a pending inactive account and queued email. The worker sends it asynchronously; check Admin-only GET `/api/users/{id}/invitation/delivery` for status, then look for a message at `http://localhost:18025`. Mailpit captures locally even though example.test is not a deliverable internet mailbox. No real email is sent.

Copy only the activation link's `#token=` value into your manual collection variable `activationToken`. Send:

```http
POST {{baseUrl}}/api/auth/invitations/validate
Content-Type: application/json
{{csrfHeader}}: {{csrfToken}}

{"token":"{{activationToken}}"}
```

Expected **200** with the invited account's details. Then send `POST /api/auth/invitations/accept` with the same headers:

```json
{"token":"{{activationToken}}","password":"Postman-coach-password-123!"}
```

Expected **200**, active account. Reusing the token → **400**. Format validation does not prove inbox existence; completing a real delivered invitation demonstrates access to that link. The local `/activate` URL is only a placeholder for a future frontend; use these backend endpoints for activation testing. The 48-request invitation collection automates this with a linked Athlete account, including resend invalidation and access restrictions.

### F. Test logout

Send `POST {{baseUrl}}/api/auth/logout` with the current CSRF header → **204**. A subsequent `GET /api/athletes` → **401**. Fetch CSRF again before another login. Logout without a token → **403**.

## 5. Run the other backend collections

Use the same proxy environment so all requests continue through Nginx.

1. Clear localhost cookies, then run the **438-request security collection** from the beginning, one iteration. Select all three top-level folders in order: **00 - Account setup and use-case checks**, **01 - All endpoints - role access matrix**, **02 - Cleanup and sign out**. Start a fresh run after reimporting so an older saved run order is not reused. It activates invited Coach and Athlete accounts through Mailpit, checks their roles and ownership through the shared login endpoint, and finishes with both synthetic accounts deactivated and the Admin logged out. The [folder-order fix and missing-ID guard](endpoint-role-access.md) prevent role checks from running before fixture creation.
2. Run the **48-request invitation collection**, one iteration. It obtains CSRF, signs in as Admin, reads only its uniquely addressed messages from Mailpit, activates an Athlete account and exercises its permitted/restricted APIs. It leaves an inactive account and reusable categories; these have no delete endpoints. Clear cookies after the run before starting another session sequence.
3. Run the **186-request functional collection** independently from folder **00 - Authentication** through **07 - Deletion and cleanup**, one iteration. Its first folder detects the server's authentication mode, gets CSRF, signs in with your environment's Admin email/password, and refreshes CSRF. It stops before CRUD when credentials are wrong or the account is not an Admin. No manual login from another collection is required.
4. Configure Postman's working directory as this `docs/postman` directory for multipart fixture paths, or select each fixture file manually under Body → form-data. CSV/TSV/XLSX fixtures are upload files, not Collection Runner iteration data. Let Postman generate multipart Content-Type and its boundary.

The functional pack covers athlete CRUD/search/history, meets/events, assignments, availability, results and labels, rankings, imports, CSV exports and cleanup. The security pack covers role restrictions; the invitation pack covers account activation and Athlete access. See [the full endpoint/folder guide](README.md) for per-feature contracts. Requests create/update/delete synthetic records and should never target real club data.

Age bands are intentionally unconfigured in this fixture. For a separate age-band test, set `POSTMAN_AGE_GROUP_BANDS` in the terminal using the club-defined `label:minAge:maxAge;label:minAge:maxAge` format, recreate the backend, and supply the explicit `ageAsOf` date to rankings and exports. The override forwards this value as `APP_AGE_GROUP_BANDS`. Do not invent club categories. Availability fixtures must be in the future relative to the meet's midnight/default or configured start time in America/Toronto.

## 6. Demonstrate externalized CORS configuration

In the same terminal, set a different allowed origin and recreate the backend:

```sh
export POSTMAN_ALLOWED_ORIGINS=http://localhost:3000
docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml \
  up -d --no-build --wait --wait-timeout 240 backend
docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml restart frontend
```

Repeat the OPTIONS request: port 5173 now returns **403**, and port 3000 returns **200**. This proves the origin is external configuration and cannot be silently re-added by a controller annotation. Restarting the backend invalidates its in-memory sessions; obtain fresh CSRF and sign in again for protected requests. Restarting Nginx ensures it resolves the recreated backend container.

To restore the focused collection's default, set `POSTMAN_ALLOWED_ORIGINS=http://localhost:5173` and repeat the commands. Alternatively update Postman `trustedOrigin` to match the configured origin. Explicitly setting `POSTMAN_ALLOWED_ORIGINS=''` and recreating the backend forwards an empty `APP_CORS_ALLOWED_ORIGINS`, allowing no cross-origin requests; same-origin proxy requests continue to work.

## 7. Docker checks Postman cannot perform

An HTTP success cannot prove the image was built with multiple stages or that Maven/Node are absent. The Dockerfiles and runtime checks provide that evidence:

```sh
docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c \
  'id; java -version; test ! -d /build && ! command -v mvn && ! command -v javac'
docker run --rm --entrypoint sh "$FRONTEND_IMAGE" -c \
  'test ! -d /build && ! command -v node && ! command -v npm'
docker image inspect "$BACKEND_IMAGE" "$FRONTEND_IMAGE" \
  --format '{{.RepoTags}} {{.Architecture}} {{.Size}}'
```

Expect UID 10001 and Java 17 in the backend, no Maven/javac, and no Node/npm in Nginx. Image size is in bytes and depends on architecture/base versions; do not compare different architectures as a size optimization measurement. Postman confirms that the resulting images serve working requests through Nginx.

## 8. Troubleshooting and cleanup

| Symptom | Check |
| --- | --- |
| Connection refused | Docker Desktop is running, stack is healthy, baseUrl uses port 18080 |
| `/api/athletes` succeeds anonymously | You used the auth-disabled CI fixture alone or retained a logged-in cookie |
| All writes return 403 | Cookie retained, correct CSRF header name, fresh token after login, no disallowed Origin header |
| Protected GET returns 401 after login | Same host throughout; cookie jar enabled; HTTP fixture uses SESSION_COOKIE_SECURE=false |
| Trusted preflight returns 403 | Exact scheme/host/port matches POSTMAN_ALLOWED_ORIGINS; backend was recreated after changes |
| `/api` shows a followed error instead of 308 | Disable automatically following redirects for that request; the focused collection already does this |
| Login returns 429 | Wait for Retry-After; cookies and backend restarts do not reset database counters. Use unique synthetic emails for deliberate failure tests. |
| Email invitation returns 503 | Configure the private outbox key; SMTP outages now leave the invitation queued instead of returning 503 |
| No Mailpit message | Check Admin delivery status, worker enabled/key/polling configuration and SMTP connectivity; PENDING retries automatically and FAILED requires fixing the cause then resending |
| Bootstrap password changed but login still fails | Existing account password is persisted; reset only this disposable database if needed |
| Import upload cannot find file | Set working directory or reselect the fixture; leave multipart Content-Type automatic |
| Role tests fail after manually switching identities | Clear cookies and rerun from the beginning; signing into another account replaces the current session identity |

Inspect status and logs without printing environment secrets:

```sh
docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml ps
docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml logs --tail=80 backend frontend mailpit
```

When finished, remove only this disposable stack:

```sh
docker compose --env-file .env.postman.local -p fa-postman \
  -f deploy/compose.ci.yml -f deploy/compose.postman.yml \
  down --volumes --remove-orphans
```

This deletes the test database and captured local mail. It does not delete the root development stack's named database volume or change AWS.

## Account-only verification

All three account roles use email/password login. New accounts are created by invitation and cannot be activated by supplying an Admin-chosen password. The initial Admin bootstrap remains the startup exception. Athlete profile links and passwordless token-exchange login have been removed; the `/api/portal/**` routes now require an activated Athlete account linked to one athlete.

The updated suites were run with Newman against the production-image/Nginx/Mailpit fixture:

| Suite | Ordered collection requests | Passing assertions |
| --- | --- | --- |
| Deployment/proxy/CORS/CSRF | 39 | 74 |
| Account roles and athlete permissions | 438 | 512 |
| Invitations and Athlete accounts | 48 | 83 |
| Functional backend, including its own Authentication folder | 186 | 355 |
| Total | 711 | 1,024 |

All suites reported zero failures. The login-throttling verification passed 711 ordered collection requests plus Mailpit polling subrequests. Network-request totals vary with delivery timing; assertions total 1,024. All four collections now use numbered root folders. Reimport all updated JSON files and start fresh runs in displayed folder order. Fixture capture and missing-variable checks in the deployment, invitation and functional collections stop failed setup before invalid-ID requests; CORS/CSRF negative tests keep their original expectations. The functional collection itself contains 186 requests, including independent Admin authentication; no additional login requests are appended. Against the explicitly authentication-disabled CI fixture, the three sign-in steps are skipped and 183 HTTP requests execute; the updated fixture-validation version passed 349 assertions in that mode. A wrong-password verification stopped after three HTTP requests, before CRUD. The backend suite separately passed 367 tests with two existing skips, including 336 endpoint role checks, 160 missing-CSRF checks, shared note persistence/privacy, rejection of removed link endpoints, invitation-only account creation, account deactivation, ownership checks, and account-attributed availability retries.

Reimport the security collection after updating: its name is now **Flying Angels - Account roles and athlete permissions**. Clear cookies and start from the beginning; signing into another account replaces the current session identity. Both role and invitation suites require the local Mailpit inbox. The desktop UI itself was not automated.

