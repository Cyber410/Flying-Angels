# Sprint 1 Test Cases

**Status:** Updated 2026-10-08 against the code on `development` and `feature/frontend-tests`. Pending team approval.

**Scope:** UC-01 Centralized Athlete Directory and Search, UC-02 Athlete Profile Retrieval and Navigation.

**IDs:** `TC-UCxx-nn` are the detailed cases. The **Plan ID** column maps each one to T01–T20 in the Sprint Testing Plan. The team has not yet chosen which scheme is official.

**Automated** means the check is written as code and reruns in GitHub Actions on every push and pull request (that rerun is the automated regression). **Manual** means a person performs or judges it: Postman, the browser workflow, accessibility and acceptance.

## Summary

| Status | Count | Meaning |
| --- | --- | --- |
| Automated | 22 | A test in the repo covers it |
| No test yet | 6 | Planned as automated, not written |
| Manual, not run | 18 | Planned as manual, no result recorded yet |
| **Total** | **46** | |

Backend tests are merged into `development` (PR #10). Frontend tests (27, Vitest + React Testing Library) are on `feature/frontend-tests`, not yet merged.

## Open defects and questions

| # | Finding | Owner to decide |
| --- | --- | --- |
| D-01 | Filters do not match across layers. The API filters by team (FLY/FLYA) and gender. The UI filters by age group and event. Each side passes its own tests, so no test proves filtering works end to end. | Dev, Joshua |
| D-02 | Search runs in two places. The API searches in the database query (`AthleteRepository.search`). The UI loads all athletes and searches in the browser, so the API search is never called by the UI. | Dev, Joshua |
| D-03 | The Sprint Plan expects "No events available". The profile page shows a different "No event results…" message. | Joshua |
| D-04 | The directory search box has a placeholder but no label (accessibility). | Joshua |
| D-05 | Running from Docker, the directory shows "Unable to load athletes". `vite.config.ts` proxies `/api` to `http://localhost:8080`, which inside the frontend container is the frontend itself, not the backend service. | Khushi, Dev |
| Q-01 | The storyboard note lists hometown. No hometown field or filter exists in the code. Dropped or still coming? | Dev |
| Q-02 | Add, edit, delete and import exist in the app but are outside the Sprint 1 plan, so they have no test cases here. In scope for Sprint 1 testing? | Dev |
| Q-03 | Sprint Plan says JUnit 5; the project uses JUnit 6.0.3 via Spring Boot 4.1.1. | Dev |

Invalid-ID cases (TC-UC02-15 to -18) are not in the Sprint Plan. They are proposed additions for the team to decide.

## UC-01 Centralized Athlete Directory and Search

| ID | Plan ID | Scenario | Expected result | Path | Level | Tool | Proven by | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-UC01-01 | T01 | Directory data loads | `GET /api/athletes` returns every seeded athlete | Sunny | Integration | Spring Boot Test / MockMvc + MySQL (Testcontainers) | `AthleteDirectoryIntegrationTest.directoryReturnsEveryAthlete`, `AthleteServiceTest.noSearchOrFilterRequestsWholeDirectory` | Automated |
| TC-UC01-02 | T01 | Open the directory page | Directory shows every athlete, no errors | Sunny | System | Browser | Supported by UI test "lists every athlete with name and athlete number" | Manual, not run (blocked by D-05 in Docker) |
| TC-UC01-03 | T02 | Full name search | Only athletes with that name are shown | Sunny | Unit | JUnit + Mockito, React Testing Library | `AthleteServiceTest.searchTextIsPassedToQueryUnchanged`, UI test "narrows the list to athletes whose name matches" | Automated (no database test uses a full name; partial match is proven in -06) |
| TC-UC01-04 | T03 | Partial name search | All athletes whose name contains the text are shown | Sunny | Unit | JUnit + Mockito | `AthleteServiceTest.searchTextIsPassedToQueryUnchanged`; matching proven in -06 | Automated |
| TC-UC01-05 | T04 | Case-insensitive search | Lowercase, UPPERCASE and MiXeD return the same results | Sunny | Unit | JUnit + Mockito, React Testing Library | `AthleteServiceTest.searchTextIsPassedToQueryUnchanged`, UI test "ignores letter case and surrounding spaces" | Automated |
| TC-UC01-06 | T03, T04 | Case-insensitive partial search against the real database | Matching athletes are returned by the database query | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteDirectoryIntegrationTest.mixedCasePartialSearchMatches` | Automated |
| TC-UC01-07 | T05 | Surrounding spaces ignored | " Name " and "Name" return identical results | Sunny | Unit | JUnit + Mockito, React Testing Library | `AthleteServiceTest.surroundingSpacesAreIgnored`, UI test "ignores letter case and surrounding spaces" | Automated |
| TC-UC01-08 | T06 | Shared names returned as separate records | Two records, each with its own ID | Rainy | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteDirectoryIntegrationTest.sameNameAthletesAreSeparateRecords` | Automated |
| TC-UC01-09 | T06 | Shared names individually selectable in the UI | Each row is a separate item tied to its own ID | Rainy | Unit | React Testing Library | None | No test yet |
| TC-UC01-10 | T07 | No-match search is an empty result, not an error | 200 with an empty array | Rainy | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteDirectoryIntegrationTest.noMatchReturnsEmptyArray`, `AthleteControllerWebMvcTest.noMatchSearchReturnsEmptyArray` | Automated |
| TC-UC01-11 | T07 | No-match search shows the empty state | "No athletes found." is shown | Rainy | Unit | React Testing Library | UI test "shows the empty state when nothing matches" | Automated |
| TC-UC01-12 | T07 | Recover from a typo | Correcting the spelling shows the athlete without reloading | Rainy | System | Browser | | Manual, not run |
| TC-UC01-13 | T08 | Valid filter narrows results | Only athletes matching the filter are shown | Sunny | Unit + Integration | JUnit + Mockito, MockMvc + MySQL, React Testing Library | API (team, gender): `AthleteServiceTest.teamFilterIsNormalised`, `.genderFilterIsTrimmed`, `AthleteDirectoryIntegrationTest.teamAndGenderFilters`. UI (age group, event): "filters by age group", "filters by event, matching event names regardless of case" | Automated per layer; see D-01 |
| TC-UC01-14 | T09 | No-match filter shows the empty state | "No athletes found." is shown; filter can be changed or cleared | Rainy | Unit | React Testing Library | None | No test yet |
| TC-UC01-15 | T10 | Search and filter combine | Every result matches both; a no-match combination returns nothing | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteDirectoryIntegrationTest.combinedSearchGenderAndTeam`, `AthleteServiceTest.searchAndFiltersAreSentTogether` | Automated (API only; no UI test combines search and filter) |
| TC-UC01-16 | T11 | Clearing search keeps the active filter | Results show all athletes matching the filter; filter still selected | Sunny | Unit | React Testing Library | None | No test yet |
| TC-UC01-17 | T11 | Clear everything restores the full directory | Full list is shown again | Sunny | Unit | React Testing Library | UI tests "restores the full list when 'Clear all' is clicked", "restores the full list when 'Clear Filters' is clicked" | Automated |
| TC-UC01-18 | T02–T10 | Search and filter requests on the running app | Responses match -03 to -15 | Both | Integration | Postman | | Manual, not run |
| TC-UC01-19 | T01–T20 | Full workflow in the browser | Directory, search/filter, profile, events, return all work in sequence | Sunny | System | Browser | | Manual, not run (blocked by D-05 in Docker) |
| TC-UC01-20 | | Keyboard-only directory use | Every control reachable with Tab/Enter/Esc; focus visible | Sunny | Accessibility | Keyboard | | Manual, not run |
| TC-UC01-21 | | Accessibility scan, with results and in the empty state | No critical issues | Both | Accessibility | Axe / WAVE | | Manual, not run (D-04 expected to be flagged) |
| TC-UC01-22 | | Regression after a change | Both use cases still work | Sunny | Regression | Browser | | Manual, not run |
| TC-UC01-23 | | Demo against the user story | Client confirms the use case is met | Sunny | Acceptance | Live demo | | Manual, not run |

## UC-02 Athlete Profile Retrieval and Navigation

| ID | Plan ID | Scenario | Expected result | Path | Level | Tool | Proven by | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-UC02-01 | T12 | Profile looked up by unique ID | The athlete with that exact ID is returned | Sunny | Unit | JUnit + Mockito | `AthleteServiceTest.profileIsLookedUpById`; UI test "opens the selected athlete's profile route" | Automated |
| TC-UC02-02 | T12, T13 | Profile retrieved from the real database | `GET /api/athletes/{id}` returns 200 with the correct athlete | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.profileMatchesStoredRecord` | Automated |
| TC-UC02-03 | T16 | Open profile from search results | Profile opens for the selected athlete's ID | Sunny | Unit | React Testing Library | None (route test exists only without a search) | No test yet |
| TC-UC02-04 | T17 | Open profile from filter results | Profile opens for the selected athlete's ID | Sunny | Unit | React Testing Library | None | No test yet |
| TC-UC02-05 | T18 | Shared name opens the correct profile | Profile shows the selected athlete's ID and details | Rainy | System | Browser | | Manual, not run |
| TC-UC02-06 | T13 | First name, last name, DOB match the record | Values match the database record exactly | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.profileMatchesStoredRecord`, `AthleteControllerWebMvcTest.profileByIdReturnsAthleteJson`, UI test "requests the athlete from the URL and shows their details" | Automated |
| TC-UC02-07 | T13 | DOB displayed consistently | Same DOB format on every profile | Sunny | Unit | React Testing Library | None dedicated to the format | No test yet |
| TC-UC02-08 | T13 | Name and DOB consistent across screens | Identical in the directory and the profile | Sunny | System | Browser | | Manual, not run |
| TC-UC02-09 | T14 | Correct events shown | All of that athlete's events are returned | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.athleteEventsAreReturned`, `AthleteControllerWebMvcTest.profileUsesCentralEventResultsWhenPresent`, UI test "shows each event with its score" | Automated |
| TC-UC02-10 | T14 | Other athletes' events not shown | Only that athlete's events are returned | Sunny | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.noOtherAthletesEventsAppear` | Automated |
| TC-UC02-11 | T15 | Athlete with no events | A no-events message is shown; name and DOB stay visible | Rainy | Unit | React Testing Library | UI test "shows a message when the athlete has no event results" | Automated; wording differs from the plan, see D-03 |
| TC-UC02-12 | T19 | App back control returns to the directory | Directory is shown | Sunny | Unit | React Testing Library | UI test "returns to the directory from a loaded profile" | Automated |
| TC-UC02-13 | T20 | Browser Back returns to the directory | Directory is shown and browsing can continue | Sunny | System | Browser | | Manual, not run |
| TC-UC02-14 | T19 | Recover after selecting the wrong athlete | Correct profile opens; no stale data | Rainy | System | Browser | | Manual, not run |
| TC-UC02-15 | Proposed | Non-existent ID (service) | Clear not-found outcome; no crash | Rainy | Unit | JUnit + Mockito | `AthleteServiceTest.nonExistentIdThrowsNotFound` | Automated |
| TC-UC02-16 | Proposed | Non-existent ID (API) | 404 `ATHLETE_NOT_FOUND`, no athlete data | Rainy | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.unknownIdReturnsNotFound`, `AthleteControllerWebMvcTest.unknownIdReturnsNotFound` | Automated |
| TC-UC02-17 | Proposed | Invalid ID format | 400 `INVALID_REQUEST`; no server error | Rainy | Integration | Spring Boot Test / MockMvc + MySQL | `AthleteProfileIntegrationTest.malformedIdReturnsBadRequest`, `AthleteControllerWebMvcTest.malformedIdReturnsBadRequest` | Automated |
| TC-UC02-18 | Proposed | Invalid ID in the browser | Clear message with a way back to the directory | Rainy | System | Browser | Supported by UI tests "shows 'Athlete not found' when the profile cannot be loaded", "rejects a non-numeric ID without calling the API", "returns to the directory from the 'not found' screen" | Manual, not run |
| TC-UC02-19 | T12–T15 | Profile requests on the running app | Valid, non-existent and malformed IDs match -02, -16, -17 | Both | Integration | Postman | | Manual, not run |
| TC-UC02-20 | | Keyboard-only profile navigation | Open a profile and return using only the keyboard; focus visible | Sunny | Accessibility | Keyboard | | Manual, not run |
| TC-UC02-21 | | Accessibility scan, with and without events | No critical issues | Both | Accessibility | Axe / WAVE | | Manual, not run |
| TC-UC02-22 | | Regression after a change | Both use cases still work | Sunny | Regression | Browser | | Manual, not run |
| TC-UC02-23 | | Demo against the user story | Client confirms the use case is met | Sunny | Acceptance | Live demo | | Manual, not run |

## Automated checks beyond the plan

These tests exist but have no case above:

- Directory UI: loading message, error message when athletes cannot be loaded, empty state with no athletes, 10 per page with page navigation, return to page 1 on a new search.
- Profile UI: loading message, "Not provided" for empty optional fields.
- Frontend API service: correct endpoints requested; errors thrown on a failed response and on 404.
- Backend, written by Dev and outside Sprint 1 plan scope: `AthleteControllerTest`, `AthleteImportServiceTest`.

## Test data

At least two athletes who share a name, one athlete with no events, athletes on FLY, FLYA and both. The integration tests seed this themselves; the Docker database currently has no seed data, so the manual pass needs athletes added first.
