package com.flyingangels.backend.event;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flyingangels.backend.DatabaseIntegrationTest;

/**
 * UC-04 integration test skeleton, from testing-drafts/sprint-2-test-cases-uc04-uc06.md.
 * Athlete-to-meet event participation. Open decisions: P1 (partial batch), P2 (remove athlete with results), P3 (age groups).
 *
 * There is no API contract for UC-04 yet, so no endpoint is assumed here. Each method is one backend-testable
 * test case (MockMvc rows only; RTL and Browser-only rows are left out) and is filled in once the contract is agreed.
 */
@Disabled("UC-04 not built yet - API contract pending")
class EventParticipationIntegrationTest extends DatabaseIntegrationTest {

	@Test
	@DisplayName("T45 expanding an event lists the athletes already assigned to it")
	void listsAthletesAssignedToEvent() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T46 assign several athletes adds all of them to this event only")
	void assignsSeveralAthletesToThisEventOnly() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T47 age group filter returns only athletes in that age group (per P3, if server-side)")
	void filtersAssignableAthletesByAgeGroup() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T48 same athlete can be assigned to a second event in the same meet")
	void sameAthleteCanJoinTwoEventsInOneMeet() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T49 athlete profile shows the meet and event they are assigned to")
	void profileShowsMeetAndEventParticipation() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T52 assigning an athlete already in the event returns 409, no duplicate row")
	void assigningAthleteAlreadyInEventIsRejected() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T53 assign with an empty athlete list returns 400")
	void emptyAthleteListIsRejected() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T55 removing an assigned athlete removes them from the event and profile only")
	void removingAthleteLeavesOthersUntouched() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T57 removing an athlete who has results in the event (per P2)")
	void removingAthleteWithResults() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T58 batch with one already-assigned or unknown athlete (per P1)")
	void batchWithAssignedOrUnknownAthlete() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T59 athlete, event or meet ID that doesn't exist returns 404 with the right code")
	void unknownAthleteEventOrMeetReturnsNotFound() {
		fail("Not written yet - pending UC-04 API contract");
	}

	@Test
	@DisplayName("T60 event ID that belongs to a different meet returns 404, neither meet changed")
	void eventFromAnotherMeetReturnsNotFound() {
		fail("Not written yet - pending UC-04 API contract");
	}
}
