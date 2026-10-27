package com.flyingangels.backend.event;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flyingangels.backend.DatabaseIntegrationTest;

/**
 * UC-05 integration test skeleton, from testing-drafts/sprint-2-test-cases-uc04-uc06.md.
 * Results and performance records. Open decisions: R1 (result format), R3 (Edit All atomicity), R4 (sort), R5 (notes).
 *
 * There is no API contract for UC-05 yet, so no endpoint is assumed here. Each method is one backend-testable
 * test case (MockMvc rows only; RTL and Browser-only rows are left out) and is filled in once the contract is agreed.
 */
@Disabled("UC-05 not built yet - API contract pending")
class ResultsIntegrationTest extends DatabaseIntegrationTest {

	@Test
	@DisplayName("T62 expanding an event shows each assigned athlete with their existing results")
	void listsEachAthletesExistingResults() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T63 saving a valid result stores it beside the athlete")
	void savesValidResult() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T64 adding another result for the same athlete creates a new record, first unchanged")
	void addsSecondResultForSameAthlete() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T65 editing a result updates the same record, no new row")
	void editingResultUpdatesSameRecord() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T66 Edit All Results saves every change in one save, unchanged rows untouched")
	void editAllResultsSavesAllChangesAtOnce() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T67 event note for an athlete is saved with a timestamp and linked to the meet and event")
	void savesEventNoteWithTimestamp() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T68 athlete profile lists every meet, event and performance, notes timestamped")
	void profileShowsMeetAndEventHistory() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T69 Results Overview search by athlete returns matching performances from all meets")
	void resultsOverviewSearchesByAthlete() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T70 Results Overview filter by event returns only that event, in the chosen order (per R4)")
	void resultsOverviewFiltersByEventAndSorts() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T72 empty or blank result is rejected, nothing saved")
	void emptyResultIsRejected() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T73 result in the wrong format is rejected, nothing saved (per R1)")
	void wronglyFormattedResultIsRejected() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T74 result for an athlete not assigned to the event is rejected, nothing saved")
	void resultForUnassignedAthleteIsRejected() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T77 Edit All Results with one invalid value saves nothing until fixed (per R3)")
	void editAllWithOneInvalidValue() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T78 removing a result removes only that result")
	void removingResultKeepsAthletesOtherResults() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T80 empty or whitespace-only note is rejected, nothing saved")
	void blankNoteIsRejected() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T82 result, athlete or event ID that doesn't exist returns 404, nothing changed")
	void unknownResultAthleteOrEventReturnsNotFound() {
		fail("Not written yet - pending UC-05 API contract");
	}

	@Test
	@DisplayName("T83 edited result shows the new value in the profile and Results Overview")
	void editedResultShownEverywhere() {
		fail("Not written yet - pending UC-05 API contract");
	}
}
