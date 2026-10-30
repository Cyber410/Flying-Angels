package com.flyingangels.backend.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * UC-04 integration tests for athlete-to-event participation, from testing-drafts/sprint-2-test-cases-uc04-uc06.md,
 * against Dev's API on development (EventController). Open decisions: P1 (partial batch), P2 (remove athlete with
 * results), P3 (age groups).
 *
 * Each method is one backend-testable test case (MockMvc rows only; RTL and Browser-only rows are left out).
 */
class EventParticipationIntegrationTest extends EventApiTestSupport {

	@Test
	@Disabled("Not written yet")
	@DisplayName("T45 expanding an event lists the athletes already assigned to it")
	void listsAthletesAssignedToEvent() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T46 assign several athletes adds all of them to this event only")
	void assignsSeveralAthletesToThisEventOnly() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T48 same athlete can be assigned to a second event in the same meet")
	void sameAthleteCanJoinTwoEventsInOneMeet() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T49 athlete profile shows the meet and event they are assigned to")
	void profileShowsMeetAndEventParticipation() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T52 assigning an athlete already in the event returns 409, no duplicate row")
	void assigningAthleteAlreadyInEventIsRejected() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T53 bulk assign with an empty athlete list returns 400 VALIDATION_ERROR, no participants created")
	void emptyAthleteListIsRejected() throws Exception {
		long event = createEvent();

		mockMvc.perform(post("/api/events/{id}/participants/bulk", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"athleteIds\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertEquals(0, participationRepository.count());
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T55 removing an assigned athlete removes them from the event and profile only")
	void removingAthleteLeavesOthersUntouched() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T57 removing an athlete who has results in the event (per P2)")
	void removingAthleteWithResults() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T58 batch with one already-assigned or unknown athlete (per P1)")
	void batchWithAssignedOrUnknownAthlete() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T59 unknown athlete returns 404 ATHLETE_NOT_FOUND, unknown event 404 EVENT_NOT_FOUND, nothing created")
	void unknownAthleteOrEventReturnsNotFound() throws Exception {
		long event = createEvent();
		long athlete = createAthlete("Jane", "Doe");

		mockMvc.perform(post("/api/events/{id}/participants", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"athleteId\":" + unknownId("athletes") + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));

		mockMvc.perform(post("/api/events/{id}/participants", unknownId("events"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"athleteId\":" + athlete + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		assertEquals(0, participationRepository.count());
	}
}
