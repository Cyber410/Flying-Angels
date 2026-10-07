# Sprint 1 Test Cases

**Status: Draft, pending team approval.**

Scope: UC-01 Centralized Athlete Directory and Search, UC-02 Athlete Profile Retrieval and Navigation.
Athlete details in scope: name, DOB, team, ID, gender, hometown, events.

**Automated** tests are Unit, Integration, and the negative (rainy) cases written inside them.
**Manual** tests are Postman (Integration level), System, Accessibility, Regression, and Acceptance.
**Regression** means two things: the full automated suite reruns in GitHub Actions on every push and pull request, and after any change the full browser workflow is repeated manually to confirm the other use case still works.

Note: Invalid-ID cases are not in the Sprint Plan and are proposed for the team to decide.

Open items:
- No Athlete classes or endpoints exist in the backend yet. Class names, endpoint paths and error status codes will be filled in once the backend team adds them.
- The frontend has no React Testing Library or `test` script set up yet.
- Sprint Plan lists profile content as first name, last name, DOB, but the storyboard note also lists team, ID, gender, hometown. Team to confirm which fields the profile shows.
- Project uses JUnit 6.0.3 (via Spring Boot 4.1.1). Sprint Plan says JUnit 5.

Test data needed: at least two athletes who share the same name, one athlete with no events, and athletes on at least two different teams.

---

## UC-01 Centralized Athlete Directory and Search

| ID | Scenario | Steps | Expected result | Path (sunny/rainy) | Level | Tool | Automated or manual |
|---|---|---|---|---|---|---|---|
| TC-UC01-01 | Directory data loads | 1. Seed test database with athletes<br>2. Request the full athlete list | All seeded athletes are returned. | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC01-02 | Open the directory page | 1. Open the app<br>2. Go to the athlete directory | Directory shows every athlete; no errors | Sunny | System | Browser | Manual |
| TC-UC01-03 | Full name search | 1. Search for an athlete's full name | Only athletes with that name are returned | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC01-04 | Partial name search | 1. Search for part of a name (e.g. first 3 letters) | All athletes whose name contains that text are returned | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC01-05 | Case-insensitive search | 1. Search the same name in lowercase, UPPERCASE and MiXeD case | All three searches return the same results | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC01-06 | Case-insensitive partial search against real database | 1. Seed test database<br>2. Send a mixed-case partial name search | Matching athletes are returned (confirms database matching, not just code) | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC01-07 | Surrounding spaces ignored | 1. Search `"  Name  "` with leading/trailing spaces<br>2. Search `"Name"` | Both searches return identical results | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC01-08 | Shared names returned as separate records | 1. Seed two athletes with the same name<br>2. Search that name | Two separate records are returned, each with its own ID | Rainy | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC01-09 | Shared names individually selectable | 1. Render results containing two athletes with the same name<br>2. Select each one | Each row is a separate, selectable item tied to its own ID | Rainy | Unit | React Testing Library | Automated |
| TC-UC01-10 | No-match search returns empty result, not an error | 1. Search a name that does not exist | Empty list returned with a success response (no server error) | Rainy | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC01-11 | No-match search shows empty state | 1. Render directory with an empty search result | A clear "no athletes found" style message is shown | Rainy | Unit | React Testing Library | Automated |
| TC-UC01-12 | Recover from a typo | 1. Search a misspelled name (empty state appears)<br>2. Correct the spelling | Empty state disappears and the correct athlete appears, without reloading the page | Rainy | System | Browser | Manual |
| TC-UC01-13 | Valid filter narrows results | 1. Apply a filter (e.g. one team) | Only athletes matching the filter are returned | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC01-14 | No-match filter shows empty state | 1. Apply a filter value no athlete matches | Empty state message is shown | Rainy | Unit | React Testing Library | Automated |
| TC-UC01-15 | Search and filter combine | 1. Seed test database<br>2. Search a name and apply a filter together | Only athletes matching **both** the name and the filter are returned | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC01-16 | Clearing search keeps active filter | 1. Apply a filter<br>2. Type a search<br>3. Clear the search | Results show all athletes matching the filter; filter is still selected | Sunny | Unit | React Testing Library | Automated |
| TC-UC01-17 | Clear everything restores full directory | 1. Apply a search and a filter<br>2. Clear both | Full directory list is shown again | Sunny | Unit | React Testing Library | Automated |
| TC-UC01-18 | Search and filter requests on the running app | 1. Start the app (Docker)<br>2. Send search, filter, and no-match requests | Responses match TC-UC01-03 to -15 | Sunny / Rainy | Integration | Postman | Manual |
| TC-UC01-19 | Full workflow in the browser | 1. Open the directory<br>2. Search and/or filter<br>3. Select an athlete to open the profile<br>4. Review the athlete's events<br>5. Return to the directory | Each step works in sequence and the directory is usable again at the end | Sunny | System | Browser | Manual |
| TC-UC01-20 | Keyboard-only directory use | 1. Using only Tab/Enter/Esc, search, filter, clear, and select an athlete | Every control is reachable and usable; focus is visible | Sunny | Accessibility | Keyboard | Manual |
| TC-UC01-21 | Automated accessibility scan | 1. Scan the directory page with results<br>2. Scan it in the empty state | No critical issues reported | Sunny / Rainy | Accessibility | Axe / WAVE | Manual |
| TC-UC01-22 | Regression after a change | 1. After any change, repeat the full browser workflow (TC-UC01-19)<br>2. Confirm UC-02 profile retrieval and navigation still work | Both use cases still work as before the change | Sunny | Regression | Browser | Manual |
| TC-UC01-23 | Demo against user story | 1. Walk through UC-01 with the client | Client confirms the use case is met | Sunny | Acceptance | Live demo | Manual |

---

## UC-02 Athlete Profile Retrieval and Navigation

| ID | Scenario | Steps | Expected result | Path (sunny/rainy) | Level | Tool | Automated or manual |
|---|---|---|---|---|---|---|---|
| TC-UC02-01 | Profile looked up by unique ID | 1. Request an athlete's profile by ID | The athlete with that exact ID is returned | Sunny | Unit | JUnit + Mockito | Automated |
| TC-UC02-02 | Profile retrieved from real database | 1. Seed test database<br>2. Request a profile by ID | Correct athlete returned with success response | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-03 | Open profile from search results | 1. Search a name<br>2. Select an athlete | Profile opens for the selected athlete's ID | Sunny | Unit | React Testing Library | Automated |
| TC-UC02-04 | Open profile from filter results | 1. Apply a filter<br>2. Select an athlete | Profile opens for the selected athlete's ID | Sunny | Unit | React Testing Library | Automated |
| TC-UC02-05 | Shared name opens the correct profile | 1. Search a name shared by two athletes<br>2. Select the second one | Profile shows the second athlete's ID and details, not the first's | Rainy | System | Browser | Manual |
| TC-UC02-06 | First name, last name, DOB match record | 1. Seed test database<br>2. Request a profile | First name, last name and DOB match the database record exactly | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-07 | DOB displayed consistently | 1. Render profiles for several athletes | DOB uses the same format on every profile | Sunny | Unit | React Testing Library | Automated |
| TC-UC02-08 | Name and DOB consistent across screens | 1. Note an athlete's name in the directory<br>2. Open the profile | Name (and DOB, if shown) is identical on both screens | Sunny | System | Browser | Manual |
| TC-UC02-09 | Correct events shown | 1. Seed an athlete with known events<br>2. Request the profile | All of that athlete's events are returned | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-10 | Other athletes' events not shown | 1. Seed two athletes with different events<br>2. Request one profile | Only that athlete's events are returned; none from the other athlete | Sunny | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-11 | Athlete with no events | 1. Render a profile for an athlete with no events | "No events available" is shown | Rainy | Unit | React Testing Library | Automated |
| TC-UC02-12 | App back control returns to directory | 1. Open a profile<br>2. Use the app's back control | Directory is shown and search/filter still work | Sunny | Unit | React Testing Library | Automated |
| TC-UC02-13 | Browser Back returns to directory | 1. Open a profile<br>2. Press the browser Back button | Directory is shown and search/filter still work | Sunny | System | Browser | Manual |
| TC-UC02-14 | Recover after selecting the wrong athlete | 1. Select the wrong athlete<br>2. Go back<br>3. Select the correct athlete | Correct profile opens; no stale data from the wrong athlete | Rainy | System | Browser | Manual |
| TC-UC02-15 | (Proposed addition) Non-existent ID (service) | 1. Request a profile for an ID that does not exist (repository mocked to return nothing) | A clear "not found" outcome; no crash | Rainy | Unit | JUnit + Mockito | Automated |
| TC-UC02-16 | (Proposed addition) Non-existent ID (API) | 1. Request a profile for an ID not in the test database | Not-found response with no athlete data (status code to be confirmed with backend team) | Rainy | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-17 | (Proposed addition) Invalid ID format | 1. Request a profile with a malformed ID (e.g. letters or symbols) | Handled as a client error; no server error or stack trace | Rainy | Integration | Spring Boot Test / MockMvc + test MySQL | Automated |
| TC-UC02-18 | (Proposed addition) Invalid ID in the browser | 1. Open a profile address with a non-existent or malformed ID | Clear message is shown with a way back to the directory | Rainy | System | Browser | Manual |
| TC-UC02-19 | Profile requests on the running app | 1. Start the app (Docker)<br>2. Request valid, non-existent and malformed IDs | Responses match TC-UC02-02, -16, -17 | Sunny / Rainy | Integration | Postman | Manual |
| TC-UC02-20 | Keyboard-only profile navigation | 1. Using only the keyboard, open a profile and return to the directory | All steps possible; focus is visible and lands sensibly after returning | Sunny | Accessibility | Keyboard | Manual |
| TC-UC02-21 | Automated accessibility scan | 1. Scan a profile with events<br>2. Scan a profile with no events | No critical issues reported | Sunny / Rainy | Accessibility | Axe / WAVE | Manual |
| TC-UC02-22 | Regression after a change | 1. After any change, repeat the full browser workflow (TC-UC01-19)<br>2. Confirm UC-01 directory search and filter still work | Both use cases still work as before the change | Sunny | Regression | Browser | Manual |
| TC-UC02-23 | Demo against user story | 1. Walk through UC-02 with the client | Client confirms the use case is met | Sunny | Acceptance | Live demo | Manual |
