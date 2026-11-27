# Sprint 4 Test Cases (UC-13, UC-14, UC-15)

Source: Sprint 4 Testing Plan, "Test cases" section (Khoa Tran, Nov 22, 2026).
Use cases, user stories, acceptance criteria and wireframes: written by Joshua (Trello "Sprint 4 Backlog" cards + *Sprint 4 Final Integrated Delivery Plan*). Expected results below come from his acceptance criteria.

76 cases cover every sunny-day and rainy-day path in the three Sprint 4 Backlog cards: 37 sunny-day, 39 rainy-day, all automated. "Live" means the case also runs against the production URL after deploy.

Open values answered by Joshua on Teams (Nov 25): autosave saves once the user stops editing (no fixed delay); all times are Toronto time (America/Toronto) for server and users; a non-nominated athlete gets 404; tablet (768 px) uses the hamburger menu like mobile. Still open: the send time of the day-before participation reminder (S4-14-14), and Clock injection in the UC-14 reminder code (needed for S4-14-07, S4-14-10 to S4-14-15).

Routes and status codes named below follow Dev's proposed Sprint 4 code-design diagrams (docs/diagrams, PR #37). They are proposals until the code is written. Four cases were added on Nov 25 from that design (S4-13-24, S4-13-25, S4-14-24, S4-14-25); the team can drop them.

## UC-13 Detailed Meet Program

| ID | Path | Scenario | Expected result | Level | Tool |
| --- | --- | --- | --- | --- | --- |
| S4-13-01 | Sunny | Admin adds two program days inside the meet date range, out of order | Both saved; returned in date order | Integration | MockMvc + Testcontainers |
| S4-13-02 | Rainy | Add a day outside the meet date range | 400; day not saved | Integration | MockMvc |
| S4-13-03 | Rainy | Add a day with a date that already exists | Blocked with a duplicate-date error | Integration | MockMvc |
| S4-13-04 | Sunny | Coach creates a session with event, category, round, report time, start time, location | Session saved and shown under its day | Integration + E2E (live) | MockMvc, Playwright |
| S4-13-05 | Sunny | Session saved with TBC report and start times | Saved, labelled TBC, does not block publish | Unit + E2E | JUnit, Playwright |
| S4-13-06 | Sunny | **+ Add Event** from Add Session, create the event, return | New event appears in the session's event list; meet and program context restored | E2E (live) | Playwright |
| S4-13-07 | Rainy | **+ Add Event**, then return without creating | No new event; athlete assignments unchanged (counts before = after) | Integration + E2E | MockMvc, Playwright |
| S4-13-08 | Sunny | Staff edits a program field, then stops editing | Draft saved automatically once editing stops (no fixed delay); "Last saved" time updates | Unit + Integration | RTL, MockMvc |
| S4-13-09 | Rainy | Staff edits a published program but does not republish | Athlete endpoint still returns the published version | Integration | MockMvc |
| S4-13-10 | Sunny | Staff republishes | Athlete sees the new version; version history shows v2 | E2E (live) | Playwright |
| S4-13-11 | Rainy | Publish with no days, or with a day that has no sessions | Publish blocked; "at least one day with one session" error | Integration + Unit | MockMvc, RTL |
| S4-13-12 | Sunny | Delete a program day and confirm | Day and its sessions removed; events and assignments intact | Integration | MockMvc |
| S4-13-13 | Rainy | Delete a program day, then cancel the confirmation | Nothing removed | Unit | RTL |
| S4-13-14 | Rainy | Athlete calls the program create/edit/publish endpoints | 403 on every call | Integration + API (live) | MockMvc, Newman |
| S4-13-15 | Rainy | Non-nominated athlete requests a published program | 404; program content never returned | Integration + API (live) | MockMvc, Newman |
| S4-13-16 | Rainy | Athlete requests a program that has only a draft | Draft content never returned | Integration | MockMvc |
| S4-13-17 | Sunny | Nominated athlete marked Unavailable opens the program | Published program visible | Integration | MockMvc |
| S4-13-18 | Sunny | Athlete opens My Schedule | Only own assigned events; conditional rounds labelled "Conditional" | Unit + E2E (live) | RTL, Playwright |
| S4-13-19 | Sunny | Athlete opens Full Program | General and day-specific instructions shown in the right places | E2E (live) | Playwright |
| S4-13-20 | Rainy | Accessibility scan: Program tab, Add Session modal, athlete views, desktop and 375 px | No serious or critical axe violations; modal reachable and closable by keyboard | E2E (live) | Playwright + axe-core |
| S4-13-21 | Rainy | Save a session without an event, round or location | Save blocked; each missing field flagged; nothing saved | Unit + Integration | RTL, MockMvc |
| S4-13-22 | Rainy | Autosave request fails (server returns 500) | Error shown saying the latest changes are not saved; a retry saves them | Unit + E2E | RTL, Playwright (route mock) |
| S4-13-23 | Rainy | Athlete opens a nominated meet's program page | Read-only view; no edit, publish or delete controls | E2E (live) | Playwright |
| S4-13-24 | Rainy | Two staff edit the same draft; the second saves with an old revision | 409; the newer save is kept, nothing silently overwritten; the UI asks the user to reload | Integration + Unit | MockMvc, RTL |
| S4-13-25 | Sunny | Admin deletes a meet that has a draft, a published program and notifications | Delete succeeds (no foreign-key error); its program days, sessions, versions and reminder records are gone; old notifications for it open an "unavailable" page (S4-14-18) | Integration | MockMvc + Testcontainers |

## UC-14 Notifications & Reminders

| ID | Path | Scenario | Expected result | Level | Tool |
| --- | --- | --- | --- | --- | --- |
| S4-14-01 | Sunny | Coach nominates an athlete | That athlete gets one notification; no other athlete does | Integration | MockMvc |
| S4-14-02 | Rainy | User A reads, marks or deletes user B's notification | Access denied; B's notification unchanged | Integration + API (live) | MockMvc, Newman |
| S4-14-03 | Sunny | Mark one notification read | Bell count drops by 1 and matches unread items | Integration + Unit | MockMvc, RTL |
| S4-14-04 | Sunny | User has 8 notifications | Dropdown shows the 5 newest, newest first | Unit | RTL |
| S4-14-05 | Sunny | All/Unread filter, then Mark all as read | Filter shows the right items; count goes to 0 | Unit + E2E | RTL, Playwright |
| S4-14-06 | Sunny | Delete a notification, click Undo within 5 seconds | Removed at once, then restored | Unit + Integration | RTL (fake timers), MockMvc |
| S4-14-07 | Rainy | Delete, wait more than 5 seconds | Not restorable | Unit | JUnit (fixed Clock), RTL (fake timers) |
| S4-14-08 | Sunny | Staff-directed meet alert fires | Every active Admin and Coach gets it; inactive staff and athletes do not | Integration | MockMvc |
| S4-14-09 | Sunny | Program published, then republished | Each nominated athlete gets a notification each time | Integration + E2E (live) | MockMvc, Playwright |
| S4-14-10 | Sunny | Clock set to 9:00 AM Toronto time (America/Toronto), two calendar days before start; athlete still Pending | Exactly one Pending reminder | Unit | JUnit + Mockito (fixed Clock) |
| S4-14-11 | Rainy | Same time, athlete already responded | No reminder | Unit | JUnit (fixed Clock) |
| S4-14-12 | Rainy | Clock at 8:59 AM, and three days before | No reminder yet | Unit | JUnit (fixed Clock) |
| S4-14-13 | Rainy | Reminder job runs twice for the same meet | Still exactly one reminder per athlete | Integration | MockMvc + Testcontainers |
| S4-14-14 | Sunny | Athlete has 3 sessions on one participation day | One consolidated reminder the day before, not three | Unit | JUnit (fixed Clock) |
| S4-14-15 | Rainy | Athlete's only event that day is a conditional final | No participation reminder | Unit | JUnit (fixed Clock) |
| S4-14-16 | Rainy | Meet cancelled (the new cancel action, not delete) before reminders are due | Affected users alerted; no later reminders sent | Integration | MockMvc + fixed Clock |
| S4-14-17 | Sunny | Click a notification | Goes to the linked meet, program, availability or schedule page | E2E (live) | Playwright |
| S4-14-18 | Rainy | Click a notification for content the user can no longer access | Unavailable or access-denied state, no crash | Unit + E2E | RTL, Playwright |
| S4-14-19 | Rainy | User with no notifications | Empty state shown; count hidden or 0 | Unit | RTL |
| S4-14-20 | Rainy | Accessibility: bell dropdown, notifications page, Undo toast | No serious or critical axe violations; dropdown opens, closes and moves focus by keyboard | E2E (live) | Playwright + axe-core |
| S4-14-21 | Rainy | Unread filter when everything is read | "All notifications read" message | Unit | RTL |
| S4-14-22 | Rainy | Open a notification that is already read | Stays read; unread count unchanged; user goes to the linked page | Unit + Integration | RTL, MockMvc |
| S4-14-23 | Sunny | Delete a read notification, then Undo | Restored with its previous read state, not as unread | Unit + Integration | RTL, MockMvc |
| S4-14-24 | Sunny | Athlete and Coach call GET /api/notifications and /api/notifications/unread-count | 200 with only their own notifications (all-role rules not blocked by staff-only API rules) | Integration + API (live) | MockMvc, Newman |
| S4-14-25 | Rainy | The same publish event is processed twice (retry) | Each nominated athlete still has exactly one notification for that publish | Integration | MockMvc + Testcontainers |

## UC-15 Role-Based Dashboards

| ID | Path | Scenario | Expected result | Level | Tool |
| --- | --- | --- | --- | --- | --- |
| S4-15-01 | Sunny | Admin, Coach and Athlete each log in | Each lands on their own dashboard | E2E (live) | Playwright |
| S4-15-02 | Sunny | Admin dashboard with seeded data | Athletes, upcoming meets, pending responses and unpublished programs match the database | Integration | MockMvc + Testcontainers |
| S4-15-03 | Sunny | Coach dashboard | All meets shown, with program status and pending counts | Integration + E2E | MockMvc, Playwright |
| S4-15-04 | Rainy | Coach opens the Users route or calls the users API | No Users tab; API returns 403 | Integration + E2E (live) | MockMvc, Playwright |
| S4-15-05 | Rainy | Coach tries to edit basic meet information | 403; edit controls hidden | Integration + E2E | MockMvc, Playwright |
| S4-15-06 | Rainy | Athlete calls the dashboard API | Only own nominated meets; no other athlete's data | Integration + API (live) | MockMvc, Newman |
| S4-15-07 | Sunny | Athlete with a Pending response clicks the CTA | Opens the existing availability screen; no inline form | E2E (live) | Playwright |
| S4-15-08 | Sunny | Athlete with no Pending responses | "All Caught Up" shown | Unit + E2E | RTL, Playwright |
| S4-15-09 | Rainy | No meets, no nominations, no published schedule | Each empty state shown, no blank cards | Unit | RTL |
| S4-15-10 | Rainy | One dashboard section's request returns 500 | That section shows an error with Retry; other sections still load | Unit + E2E | RTL, Playwright (route mock) |
| S4-15-11 | Sunny | Recent Updates panel | Shows the user's own UC-14 notifications | Integration | MockMvc |
| S4-15-12 | Sunny | Desktop navigation per role | Admin: 8 tabs incl. Users; Coach: same minus Users; Athlete: My Meets, My Profile; no sidebar | E2E (live) | Playwright |
| S4-15-13 | Sunny | 375 px phone width per role | Hamburger menu with the same role links; tables readable | E2E (live) | Playwright (mobile viewport) |
| S4-15-14 | Rainy | Accessibility: all three dashboards, desktop and 375 px | No serious or critical axe violations; hamburger usable by keyboard | E2E (live) | Playwright + axe-core |
| S4-15-15 | Sunny | Admin uses each quick action: Add Athlete, Create Meet, Manage Users, Import CSV | Each opens the existing feature | E2E (live) | Playwright |
| S4-15-16 | Sunny | Coach uses each quick action: event assignments, results, edit program, availability | Each opens the existing feature; Manage Users not offered | E2E (live) | Playwright |
| S4-15-17 | Rainy | Athlete requests another athlete's profile or meet by URL or API | Access denied; athlete stays on own records | Integration + API (live) | MockMvc, Newman |
| S4-15-18 | Rainy | Admin or Coach with no outstanding actions and no notifications | Completed or empty state in each section | Unit | RTL |
| S4-15-19 | Rainy | Athlete with no nominated meets | Empty state; My Profile and notifications still reachable | Unit + E2E | RTL, Playwright |
| S4-15-20 | Sunny | Tablet width (768 px) per role | Hamburger menu like mobile, with the same role links; layout adapts | E2E (live) | Playwright (tablet viewport) |

## Cross-feature flows

These are Joshua's six demo scenarios, written as Playwright tests against the live site. The two reminder-timing scenarios cannot wait two real days, so their timing is proven by S4-14-10 to S4-14-15; the live test checks the event-driven part only.

| ID | Path | Scenario | Expected result | Level | Tool |
| --- | --- | --- | --- | --- | --- |
| S4-X-01 | Sunny | Coach creates a missing event via + Add Event and schedules it | Session saved; athlete assignments unchanged | E2E (live) | Playwright |
| S4-X-02 | Rainy | Admin edits a published program and tries to publish an empty draft | Athlete view unchanged; publish blocked; republish after fix updates athlete view | E2E (live) | Playwright |
| S4-X-03 | Rainy | Conditional final in the program | Shown with a Conditional label; no participation reminder created | E2E (live) + Unit | Playwright, JUnit |
| S4-X-04 | Sunny | Publish a program | Nominated athlete sees a new notification and the program from it | E2E (live) | Playwright |
| S4-X-05 | Rainy | Cancel a meet (the new cancel action, not delete) | Affected users alerted; scheduled reminders removed | E2E (live) + Integration | Playwright, MockMvc |
| S4-X-06 | Sunny | Athlete with nothing Pending logs in on desktop and phone | All Caught Up; correct navigation on both | E2E (live) | Playwright |

No case is planned as manual. If one ends up manual, this file must say why in one line (for example, "automated scan cannot judge whether a label makes sense").
