# Sprint 3 implementation notes


## Concise Sprint 3 browser coverage

Added nine Playwright checks under e2e alongside the seven export checks:
Admin invitation, email verification before account creation, athlete redirect
away from Users, password recovery, personal best/progression, scoped ranking
filters, athlete availability, stale-response reload/reconfirmation, and staff
filters/external reply source. Shared fictional sessions/data are in helpers.ts;
all API calls are intercepted with a mock 404 for unmatched requests.

Build and ESLint passed, and the final Chromium run passed all 16 tests in
30.5 seconds with no automatic retries. Initial selector mismatches were fixed
using control roles/prefix labels. One subsequent run hit a transient browser
ERR_NETWORK_CHANGED while loading a stylesheet; the final full rerun passed.
No application/backend code changes, emails, or database writes were needed.
e2e/README.md lists coverage, scope, and the headed/debug commands.

## Results loading performance and Playwright type checking

Results Overview previously requested the full event list, athlete directory,
and 108 populated event profiles in batches of six. The event repository's
collection fetch graph joined results and participations together, multiplying
database rows. A local Chromium measurement took 21,543 ms and 113 API requests
before the results summary appeared.

Added staff-only GET /api/results/overview, retaining the existing paginated
GET /api/results API. The snapshot uses three data queries: lightweight event
metadata, grouped participant counts, and meet-linked results with their to-one
athlete/category/event/meet context. It avoids collection joins and includes
archived-athlete history. The frontend assembles the snapshot with its existing
best-attempt selection, unit formatting, event-date age groups, filtering, and
sorting. Refresh always reads the server; no persistent results cache was added.
No database migration or saved result changes were required.

After rebuilding/restarting both services, initial loading measured 4,525 ms;
a warmed-up load measured 2,040 ms with a completed results request of 294 ms.
There were four API requests including session checks and React development
StrictMode's aborted request, and zero per-event profile requests. Both runs
returned the unchanged summary: 4,896 best results, 128 athletes, five meets.
The snapshot included all 5,057 linked records; existing display rules produced
5,026 displayed attempts. Live checks also verified athlete filtering and 401
for unauthenticated access. Measurements used temporary staff accounts removed
afterward and did not change athlete/results data or send email.

Validation: build, ESLint, and all 145 frontend tests across 34 files passed;
backend compilation passed without backend test suites. Four new frontend
service checks cover the single request, time/field best selection, demographics,
empty data, error handling, and cancellation. The snapshot intentionally retains
the current client-side filtering model; server pagination remains the next step
if data volume grows substantially.

The Playwright files were also outside the project's TypeScript includes, causing
editor inference errors. Added playwright.config.ts and e2e/**/*.ts to the Node
TypeScript project and an explicit CSV-helper return type. Build and ESLint
passed afterward, now checking these test files as part of the normal build.

## Playwright export browser tests (November 17, 2026)

Installed @playwright/test 1.65.1 and its matching Chromium browser locally.
The package.json and lockfile include Playwright and the test:e2e script.
All seven teammate export browser tests passed in Chromium on Windows:
option/count display, registration CSV, results CSV with multiple attempts,
empty registration, empty results, switching export types, and Cancel.
Download checks now read the browser's actual saved CSV bytes rather than only
the mocked HTTP response. Build and ESLint also passed afterward.

Tests launch their own server at http://127.0.0.1:5180 with two workers, leaving
the running application at 5173 alone. They use a fictional authenticated Admin
and simulated API responses; they do not exercise the real database, send email,
or prove backend CSV-generation correctness. Traces/screenshots are retained on
failure; HTML reports and test results are ignored by Git and Docker.

From frontend, rerun with npm run test:e2e. For a visible browser, use
npm run test:e2e -- --headed. View the last report with
npm exec -- playwright show-report. On a fresh checkout, run npm ci and
npm exec -- playwright install chromium first. Node 22 remains recommended for
the full frontend toolchain; Playwright and Vite themselves support the installed
Node 20.19.5 and the browser run passed on it. No backend tests were run.

Fresh npm audit now reports 12 high-severity dependency entries tracing to one
picomatch ReDoS advisory (GHSA-fc5q-39xc-vjpf), updated November 17. This is an
existing Vite/Vitest tooling dependency, not introduced by Playwright. GitHub's
advisory lists affected versions through 4.0.7 and no patched release:
https://github.com/advisories/GHSA-fc5q-39xc-vjpf . No force downgrade or
unverified override was applied. The prior zero-audit result was a point-in-time
check; the current audit is not clean. Do not accept untrusted glob patterns in
tooling; monitor the upstream fix. This is not a full exploitability assessment.

## Meet CSV export integration from teammate branch

Fetched origin/feature/meet-export-csv at 93e6e28, containing 72855aa
(CSV export dialog) and 93e6e28 (Playwright coverage). origin/development still
points to d89808b. Integrated the source changes into the existing uncommitted
working tree; no merge commit, local commit, or push was made.

Meet Details now offers Export to users with the EXPORT permission, alongside
the existing availability link and role-aware Edit action. The teammate's
registration/results dialog and CSS use the already present backend export
endpoints. Adapted its service to the session-aware apiFetch helper. Preserved
the current patched lockfile instead of replacing it with the branch's older
dependency snapshot. Playwright mocks now include the integrated Admin session;
its server uses isolated port 5180 instead of the running application's 5173.

The initial request to install the incoming Playwright dependency was declined,
so browser testing was initially deferred while keeping package.json and the
patched lockfile synchronized. The frontend container was subsequently rebuilt
and restarted with the integrated export UI. Playwright installation was later
authorized and completed; see the browser-test results above.

Source validation with existing Node 22 dependencies passed build, ESLint, and
the 137 existing frontend tests. Four added export-service tests passed separately
(session-aware options, CSV bytes/server filename/object-URL cleanup, empty-export
errors, and Unicode/fallback filenames). Backend source was unchanged and no
backend tests were run.

## Password recovery (November 17, 2026)

The login page now links to /forgot-password. Users request a six-digit code,
keep the recovery page open, and submit that code with a confirmed replacement
password. All active account roles can recover access. This does not create or
reactivate accounts and does not automatically sign users in.

Backend POST /api/auth/password-reset/request returns an opaque challenge and
the same generic message for known, unknown, inactive, and rate-limited emails.
SMTP delivery runs asynchronously; fast responses receive a small asynchronous
minimum delay. Requests use separate durable account/IP throttle buckets (five
per account and twenty per IP per fifteen-minute window), with at least sixty
seconds between issued codes. Code completion uses POST
/api/auth/password-reset/complete, with CSRF still required on both endpoints.

Flyway V3 adds one recovery record per account, removed on account deletion.
Random challenge tokens and token-bound codes are hashed; queued email codes
are encrypted using the existing external outbox key. Codes expire after ten
minutes, permit five wrong guesses, and are single use. Replacement challenges
invalidate previous ones. Account identity/security changes invalidate recovery.
Delivery has a database lease and bounded retries, using the existing SMTP
settings. Completion clears the queued code, queues a password-change email,
uses the existing BCrypt password policy, invalidates previous sessions through
the account security version, and clears only that account's login block.

Validation: frontend build and lint passed; 137 tests across 32 files passed.
After the final navigation adjustment, build/lint and the six recovery tests
passed again. Backend compilation passed with backend tests skipped. A temporary
backend with SMTP polling paused applied V3 and verified recovery using temporary
fictional accounts: real generated/encrypted code, old password/session rejection,
single-use behavior, persisted five-guess lockout, expiry, inactive-account denial,
generic known/unknown responses, login-block clearing, and queued confirmation.
Fixtures were removed. No actual email was sent during verification; inbox
receipt remains a manual check through the application's Forgot password flow.

## Athlete profile filters

Portal Rankings replaces the unconfigured age-group column with optional age
bands and gender filters. Bands translate to existing numeric API bounds
(Under 5, 5-14, 15-18, 19+), evaluated on the selected event date. These are
UI age ranges, not newly configured club competition categories. Filtering
requests a complete server-ranked cohort and resets pagination; all ages and
genders remain the default. No backend changes were needed.

Personal Bests now filters event series by event and result type while preserving
the server's all-time bests and keeping progression choices synchronized. Portal
Meets & Results filters by meet, event, and competition versus historical marks,
with matching counts, empty states, and reset actions. These controls use only
the athlete's already scoped records and expose no additional competitor data.

Validation: build and lint passed; all 131 frontend tests across 30 files passed.
After the final table-cell cleanup, build, lint, and the five rankings component
tests passed again. The frontend image was rebuilt for the updated UI.

## Dependency advisory updates (November 16, 2026)

Updated dependencies within the existing package.json ranges, without forced
major upgrades or overrides. The lockfile now resolves Vite 8.3.8, ESLint
10.15.0, typescript-eslint 8.73.0, undici 8.12.4, Zod 4.6.7, and minimatch
11.0.9 through updated parent dependencies. The npm audit count fell from
12 (3 high, 9 moderate) to zero, including development dependencies.

The development Dockerfile now uses npm ci, and frontend/.dockerignore excludes
local node_modules, build output, and environment files. This keeps the image
installation consistent with the reviewed lockfile. Local dependencies were
also synchronized using npm ci. Local Node 20 reports engine warnings for test
dependencies; use a current Node 22 release (22.22.2 or newer) for development
and tests, matching the container's supported runtime.

Validation in the rebuilt Node 22 image: production build, ESLint, all 126
frontend tests across 29 files, and npm audit passed. No backend tests were run.

## External SMTP only

Removed the local email-capture service and mail profile from root Compose and
the disposable Postman override. SMTP examples/defaults use Gmail on 587 with
authentication and STARTTLS; private sender credentials are unchanged. Removed
this project's `flying-angels-mailpit-1` container. Root application services are
frontend, backend, and MySQL; both Compose configurations validate and the
frontend API proxy returns HTTP 200.

Older inbox-polling Postman collections remain explicitly labeled legacy
references and cannot run against Gmail. They are separate from the CI functional
collection, which is unchanged. The disposable Postman override defaults outbound
email to disabled so synthetic recipients do not trigger real delivery. Historical
capture verification notes are retained as history; current setup instructions
use real SMTP and the frontend verification-code flow. No email was sent during
this configuration change, and no backend test suite was run.

## Latest fictional availability spread

`deploy/sql/seed-varied-demo-availability.sql` supersedes the earlier 50/50 demo
mix. Counts are Available / Pending / Unavailable: Grey Autumn 81/29/18,
Ontario Academy 76/32/20, Lagrasse 89/25/14, Etobicoke 84/28/17,
Oakville Central 92/23/13, and Oakville West 79/30/19. All 18 status counts are
different, each meet has more Available than Pending, and all include Unavailable.
Existing current answers were kept; only pending requests received fictional
audit-backed replies. Amara was excluded and her request fingerprint/audit count
remained unchanged. No messages were sent. Live API checks verified the spread
and agreement between Meets badges and Availability. Data-only update: refresh
the screens; no rebuild required.

## Meets scheduling interface

Meets now opens on Upcoming, with Past and All schedule toggles backed by the
existing `/api/meets?view=...` endpoint. Upcoming meets show nearest first;
Past/All retain most-recent-first ordering. The existing compact table keeps
search, overlapping date filters, pagination, and role-aware create/edit/delete.
Each meet adds labeled Available/Awaiting/Unavailable badges, the nominated
athlete total, and a View responses link to its filtered Availability overview.
New Meet and Refresh sit beside the page heading. Empty calendar scopes offer
View all meets; missing summaries are never displayed as invented zero counts.

Saving meet changes reloads the overview, including response counts that may be
invalidated by date/location changes. Creation switches to All so a newly created
past or future meet remains visible. No backend changes were required.

Build and lint passed; all 126 frontend tests across 29 files passed in Node 22.
New checks cover schedule transport, scope switching, summary links/counts, and
refreshing invalidated confirmations while preserving existing CRUD checks.
Live read-only API verification confirmed two upcoming/four past meets and
matched every meet's badge totals against its Availability overview. Frontend
rebuilt and restarted. No backend test suite was run.

## Balanced demonstration responses and Rankings wording

Applied `deploy/sql/seed-balanced-demo-availability.sql` locally: each of the six
existing meets now has 64 current Available responses, with 64 pending in five
meets and 65 pending in Etobicoke (129 nominations). The script tops up to half
per meet, preserves existing current answers, appends staff audit records marked
as fictional demo confirmations via Other, and sends no messages. Historical
confirmations use dates before the meet. Every request for Amara (#448) is
excluded; before/after fingerprints and audit counts confirmed no changes.
Staff Rankings now labels its discipline selector Event.

## Availability summary filter correction

Summary aggregation now shares the repository's exact meet, schedule, status,
search, and effective-status predicates with the paginated table. Totals cover
all matching pages rather than only visible rows. Selecting a specific meet or
following its availability link defaults Schedule to All; explicit Upcoming
filtering on a past meet shows zero counts and a helpful Past/All suggestion.

Verified live: Lagrasse has 128 nominations under Past/All and zero under
Upcoming. Global Upcoming contains 256 nominations. Search, status, and paging
checks confirmed summary totals equal matching table totals. Existing records
were retained. Build/lint and all 122 frontend tests passed; containers rebuilt
and restarted. No backend test suite was run.

## Local fictional profile demonstration data

`deploy/sql/seed-amara-profile-demo.sql` enriches existing Amara Adebayo (#448)
using existing fictional athletes and meets. Applied locally on 2026-11-16:
15 event assignments across six meets; 100m, 200m, and long jump; 15 competition
attempts (14 valid and one foul); five participation/performance notes; completed
events with 11–14 ranked athletes. It fills gender/statuses, expands the general
note, and corrects only the two identified unrealistic 100m attempts to 14.12s
and 13.98s. Her existing DOB is retained. Existing imported historical marks stay
unlinked. Upcoming events receive assignments without new results; previously
entered upcoming 100m attempts are retained with the corrected times.

The SQL is an explicitly local seed, not a Flyway migration. It targets the named
athlete and current local IDs, preserves existing competition marks, gives seed
attempts stable client IDs, and synchronizes nomination counts and revisions.
No login accounts are created or changed. Refresh the UI; no rebuild is needed.

## Scope and latest decisions

- Preserve Sprint 2 athlete, meet, result, import, archive, and note workflows.
- Export belongs to another teammate and is outside this frontend work.
- New account creation requires an emailed code entered in the application.
- Guardians use the linked Athlete account; there is no separate Parent role.
- Portal rankings must be restricted on the server to meets/events involving the
  linked athlete, showing rank and other competitors' names without profile access.
  Implemented in the My Profile Rankings tab, with server-side ownership checks.
- Athlete availability responses and staff availability management are implemented
  in Increment 4. Season filtering remains disabled until agreed.

## Increment 1: accounts, session access, and verified invitations

New routes:

- `/login`: email/password login with session-aware staff/athlete redirection.
- `/activate#token=...`: email verification, password confirmation, account creation.
- `/users`: Admin-only account management and separate pending invitations.
- `/portal` and `/portal/profile`: restricted, read-only foundation using the
  authenticated athlete's own profile and nominated meets. Personal Bests and
  scoped rankings extend My Profile below; My Meets supports availability replies.

## Increment 2: Personal Bests and performance progression

Staff profiles retain Overview, Meets & Results, and Notes, with a new Personal
Bests tab between history and notes. `/athletes/{id}?tab=personal-bests` opens it
directly for future rankings integration. Athlete/guardian My Profile has read-only
Overview, Meets & Results, and Personal Bests tabs, with no staff Notes or links
to staff meet details. The Rankings increment adds a fourth tab for portal users.

## Increment 3: athlete-scoped competition rankings

My Profile > Rankings provides meet/event selectors, server-calculated positions,
best valid results with units, tied ranks, paging, retry/empty states, and a
highlighted “You” row. Competitor names are plain text with no profile links.
It uses existing competition data; no mock ranking records are inserted.

`PortalRankingScope` limits category, meet, and event discovery to events with
the authenticated athlete's participation or recorded result. Portal ranking
requests now require an authorized `eventId`; arbitrary category-wide requests
are rejected, and unrelated events return 403. Staff rankings remain unchanged.
The frontend sends the event date as the age reference. Ranking policy stays in
the existing backend service; the browser does not recompute positions or ties.

The existing portal ranking contract intentionally narrows: callers must supply
`eventId`, and optional category/meet IDs must match that authorized event.
Any older backend checks expecting unrestricted portal rankings need adjustment;
no backend tests were modified or run, per the project preference.

Validation: frontend build and lint passed. Windows Node 20 could not initialize
the jsdom test workers (`markAsUncloneable`); the frontend suite was run in the
application's Node 22 container instead: all 104 tests across 24 files passed.
A live API check with a temporary linked
athlete account confirmed scoped choices, an own-athlete rank, omission of
competitor profile IDs, and rejection of unrelated/unscoped requests. The
temporary login was removed and no emails or competition fixtures were created.

`performanceService.ts` reads the existing `/api/athletes/{id}/history` for staff
and `/api/portal/history` for the authenticated athlete. The portal request never
accepts another athlete's ID. The UI uses the backend's personal-best and
progression summaries directly, preserving validity checks, ties, unit separation,
ranking direction, and best-per-competition selection. Unlinked historical marks
are not treated as competition bests.

`AthletePersonalBests` shows event, best, meet, and date with an event selector.
`PerformanceProgression` provides a dated SVG chart and a matching accessible
table. Movement upward means improvement for both timed and measured events.
Distinct states cover no competition history, events without results, invalid-only
results, a single competition, failed loads, and retries. Reopening the tab or
using Refresh reloads saved results. No backend changes or database seed data
were needed for this increment.

`AuthProvider` loads `/api/auth/me`, tracks permissions, handles sign-out and clears
protected content on HTTP 401. `RequireSession` gates routes. Staff pages hide
Admin-only athlete/meet actions from Coaches. The backend remains authoritative.
All existing API services now use `apiClient.ts`: same-origin cookies, a current
session CSRF token for each write, and no browser-stored authentication tokens.
Existing domain-service tests isolate this transport; dedicated transport tests
exercise CSRF preparation, multipart boundaries, and expired-session notification.

## Verification and API contract

An Admin creates a pending invitation containing name, email, role, and optional
athlete link. This is **not** a `staff_users` account. Email carries a private
setup link plus a six-digit verification code. The code lasts 10 minutes; the
invitation lasts 48 hours. Server-side checks require successful verification
and a matching, sufficiently long password before inserting the actual user.
Athlete links and email uniqueness are rechecked transactionally at completion.

Codes and link tokens are hashed, not returned to the frontend. Outgoing secrets
are encrypted using the existing mail-outbox key. The existing worker handles
delivery/retries. Five incorrect codes lock the invitation; resends have a
one-minute cooldown and a five-email limit. Admins can cancel and replace a
locked invitation. Admin resends replace the setup link; recipient resends keep
the link while replacing the code. Earlier codes stop working. Consumed or
cancelled invitations cannot create another account. Completion also checks that
the inviting Admin is still active and authorized.

- `GET/POST /api/users/pending-invitations`: Admin list/create.
- `POST /api/users/pending-invitations/{id}/resend`: Admin resend.
- `DELETE /api/users/pending-invitations/{id}`: Admin cancellation.
- `POST /api/auth/enrollment/info`: `{ token }`.
- `POST /api/auth/enrollment/verify`: `{ token, code }`.
- `POST /api/auth/enrollment/resend`: `{ token }`.
- `POST /api/auth/enrollment/complete`: `{ token, password }`.

`POST /api/users` and `POST /api/users/invitations` now delegate to the verified
invitation workflow and return an invitation summary rather than a newly created
user. Existing callers/contract assertions that expect an immediate user need
updating. Existing accounts and the legacy activation endpoints remain available;
the new setup page uses the verified enrollment contract. The initial bootstrap
Admin remains the deployment-provisioned exception to invitation-based creation.

## Database and runtime

Flyway V2 adds `account_invitations` and allows existing encrypted outbox entries
to refer to an enrollment before a user exists. V1 is unchanged. Deployment must
allow Flyway target `latest` (or at least `2`). Containers were subsequently
rebuilt and started locally at the user's request; see runtime verification below.

Use the existing private environment configuration for Admin/database credentials.
Keep `APP_REQUIRE_AUTHENTICATION=true`. Configure `APP_MAIL_OUTBOX_KEY` and SMTP.
The local setup uses Gmail SMTP on port 587 with authentication and STARTTLS.
Set the sender, username, and Google App Password only in the ignored `.env`.
Start the normal stack with `docker compose up -d`; no mail profile or local
inbox service is included. No passwords, email codes, or private keys belong in Git.

## Verification

Backend production sources compile with Java 21. No backend tests were added or
run, following the user's instruction. Frontend production build and lint pass;
all 86 tests pass across 19 files (75 existing plus 11 new account/transport checks).
Local runtime verification on 2026-11-16 passed: frontend/backend/MySQL/Mailpit
running, V1 baseline and V2 migration successful, Admin login through the Vite
proxy, verification email captured in Mailpit, no account before verification,
incorrect-code rejection, successful verification/password setup, used-invitation
rejection, Coach access restrictions, and logout. The fictional verification
account and its invitation/outbox records were removed afterward. No backend
test suite was run. This initial capture-based verification predates the switch to Gmail described below.

Gmail SMTP is configured locally for `solutionsetco@gmail.com` using port 587
with STARTTLS and credentials stored only in the ignored `.env` file. The user
confirmed receipt of a verification email after updating the App Password.
Standalone SMTP diagnostics timed out, so actual recipient confirmation is the
delivery evidence. Recipients on other devices still need a reachable application URL.

### Separate local sessions and account deletion

- Use `http://localhost:5173` for Admin and `http://127.0.0.1:5173` for a
  recipient account. Cookies are separated by hostname, not port. New invitation
  emails use `http://127.0.0.1:5173/activate`; previously sent links retain their
  original hostname until the invitation is resent.
- Admins can delete a login account from Users after confirmation. The backend
  removes the account, revokes legacy invitations, and cancels outstanding
  enrollment invitations for its email or issued by that account, including
  queued mail. Athlete records, results, and notes are retained.
- Self-deletion is blocked, as is deletion of the last active Admin. Session
  identity is revalidated on each request, so deleted accounts cannot reuse an
  existing session or log in again. Previously consumed invitation links remain
  unusable. A new invitation is required to grant access again.
- Frontend build and lint passed. A manual live check used one cookie jar across
  both hostnames to confirm simultaneous Admin/Coach sessions, Admin-only
  deletion, self-deletion protection, immediate session rejection, and blocked
  subsequent login. Its fictional account was removed afterward; no email was
  sent by the check and no backend test suite was run.

The pre-existing ten-table database matched the team's documented previous
schema. A private recovery dump was taken outside the repository, the reviewed
additive upgrade was applied, and one-time V1 baselining was disabled again after
V2 succeeded. All 131 athletes and 222 results were retained. Private `.env`
configuration reuses the existing database credentials and stores the generated
bootstrap Admin password and mail-outbox key. The bootstrap email is
`admin1FA@gmail.com` (stored normalized as `admin1fa@gmail.com`).

Local CORS correction: Vite rewrites the upstream Host, while browser POSTs keep
the frontend Origin. The private environment and sample configuration explicitly
allow `http://localhost:5173` and `http://127.0.0.1:5173`. Login and verification
were rechecked with that Origin header present; all runtime checks passed.

Increment 2 checks: frontend production build and lint pass; all 100 tests pass
across 23 files. Fourteen new checks cover best-versus-latest display, invalid and
empty results, single-competition messaging, event switching, time/distance chart
direction, same-day/flat series, units, own-athlete API routing, and portal tab
accessibility. A read-only check of existing live athlete history confirmed a
personal best and a multi-competition progression series. No seed data or backend
test suite was used. The frontend container was rebuilt for the new UI.

## Increment 4: meet availability and staff response overview

- `/portal` now uses `PortalMeetsPage` and the existing
  `GET /api/portal/availability` endpoint for upcoming nominations. Cards show
  assigned events, current availability, response deadlines in the academy time
  zone, and Respond/Update response actions. Closed meets cannot be submitted;
  changed assignment revisions require reconfirmation.
- `/availability` is staff-only under `READ_STAFF`, linked from navigation and
  Meet Details. The staff overview uses `GET /api/availability` with meet,
  upcoming/past/all, status, athlete search, and server pagination. Summary totals
  use the same filters as the table across all pages (corrected after the initial
  implementation, which counted past requests even in Upcoming).
- The shared `AvailabilityResponseDialog` reloads the current request before
  entry and sends its exact `version` and `assignmentRevision`. Portal replies
  use `PUT /api/portal/availability/{id}/response`. Staff replies use
  `PUT /api/availability/{id}/response`, require an external source, and allow
  an optional staff-only note. A 409 blocks submission until the proposal is
  reloaded; there is no automatic write retry.
- Staff can open the append-only response history through
  `GET /api/availability/{id}/responses`, showing portal/external sources,
  timestamps, proposal revisions, and private staff notes.
- No backend changes were needed for this increment. No mock data is used by
  these screens. Build and lint passed; all 112 frontend tests across 27 files
  passed in the Node 22 container. Eight new checks cover portal status cards,
  explicit choices, response sources, closed windows, conflict reloads, staff
  filtering, and history. Live API verification used a temporary fictional
  athlete/login/nomination, confirmed saves, stale-version rejection, history,
  filtering/count updates, ownership checks, and private-note separation, then
  removed the fixture. No email was sent and no backend test suite was run.
- Frontend image rebuilt and restarted. Export remains outside this work.


## Increment 5: staff athlete rankings

- `/rankings` is inside the existing staff route guard and is linked from staff
  navigation. It reads `/api/rankings/categories`, `/meets`, `/events`,
  `/api/age-groups`, and the existing paginated `/api/rankings` endpoint.
- Staff can filter discipline, meet, competition event, gender, and age. Club
  bands are shown only when configured; otherwise numeric ages 0–150 are used.
  The age reference is explicit and editable; choosing an event initially uses
  its competition date. Season controls are omitted because backend season
  attribution is not configured.
- Rows display backend ranks/ties, units, age or configured group, gender, meet,
  date, archived badges, and staff-only profile links opening Personal Bests.
  The backend remains responsible for best-result eligibility, comparison
  direction, tie handling, filtering, and global positions before pagination.
  Portal ranking access and scoped metadata remain unchanged.
- Corrected the local LONG JUMP category (#4) from ASC to DESC to match its
  existing meet events. This makes category-wide rankings include the correctly
  ordered long-jump results. The demo seed records the same guarded correction.
  No backend source changes or migrations were needed in this increment.
- Build and lint passed. All 118 frontend tests across 29 files passed in Node
  22. Six new checks cover staff links, filters, age modes, units/direction,
  errors/empty states, and transport queries. Read-only live API verification
  confirmed ascending sprint ranks, descending all-meet long-jump ranks, ties,
  and gender/age filtering. No backend test suite was run.
- Frontend rebuilt and restarted. Export and availability email notifications
  remain outside the implemented screens; availability replies work in-app.


## Expanded fictional roster coverage (2026-11-16)

Applied `deploy/sql/seed-expanded-fictional-roster.sql` to the local database after
staff filters exposed sparse competition coverage. No frontend code changed.

- Kept all 131 existing athletes, including the three archived records. All 128
  active athletes now have competition history. Fingerprint checks confirmed
  unchanged names, DOBs, IDs, archive flags, and all pre-existing result rows.
- Filled 11 blank fictional genders deterministically (without name inference),
  missing FLY/FLYA statuses, and blank general notes. Existing filled values and
  coaching notes were retained.
- Added 4,899 fictional attempts: 4,869 valid marks and 30 fouls, across all 31
  disciplines and 98 historical competition events. Three primary disciplines
  (100m, 200m, long jump) cover 128 athletes each; most secondary disciplines
  cover 40, selected evenly across gender and four-year age strata. 800m has 42
  after retaining existing competitors. The seed includes repeated competitions,
  modest improvement, extra attempts, and supplemental performance notes.
- Expanded the programs of the three historical demo meets to existing event
  categories. Other meets retain their programs. Upcoming meets gain assignments
  only: zero new future-dated results were created, no accounts/invitations were
  created, and no email was sent. Imported historical marks remain unlinked.
- Corrected the six standard distance-measured field categories to DESC, and
  aligned their three historical demo meet event definitions. Nomination counts
  and proposal revisions were synchronized; existing response history was kept.
  New event assignments may require athletes to reconfirm an earlier answer.
- Live API checks across all 31 disciplines confirmed at least four Female,
  age-12–15 competitors within Grey Autumn Open alone; the primary disciplines
  return 17 in that cohort. Verified meet/gender/age filters, field ranking
  directions, and non-overlapping 25-row ranking pages. Nomination consistency
  checks passed. No backend test suite was run; this was a data-only update.
- Refresh existing screens to load the new records. No rebuild is required.
  Both demo SQL files are local fixtures, not production/Flyway migrations.


## Descending staff rank display

Staff Rankings includes a Rank order control: ASC (rank 1 first) or DESC
(highest numeric rank first). `/api/rankings` accepts `rankOrder=ASC|DESC`,
with ASC retained as the default. The backend calculates best performances,
positions, and ties using the event policy, then sorts the complete ranked list
before pagination. Tied rows retain their alphabetical order. Sprint/field
comparison rules and original numeric ranks do not change. Portal rankings
retain their existing ascending display and ownership restrictions.

Build and lint passed. Live read-only checks verified two descending pages for
100m and long jump, preserved athlete ranks and scores, non-duplicate pages,
and rejection of invalid directions. No backend test suite was run.


All 120 frontend tests passed across 29 files using two workers in Node 22.
The initial concurrent run had one async UI timeout while containers were
rebuilding; limiting test workers resolved it without changing assertions.
Containers were rebuilt and restarted with the feature.
