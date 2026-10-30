package com.flyingangels.backend.event;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * UC-05 integration tests for results and performance records, from testing-drafts/sprint-2-test-cases-uc04-uc06.md,
 * against Dev's API on development (EventController). Open decisions: R1 (result format), R3 (Edit All atomicity),
 * R4 (sort), R5 (notes).
 *
 * Each method is one backend-testable test case (MockMvc rows only; RTL and Browser-only rows are left out).
 */
class ResultsIntegrationTest extends EventApiTestSupport {

	private static String resultJson(long athleteId, String result) {
		return "{\"athleteId\":" + athleteId + ",\"result\":\"" + result + "\"}";
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T62 expanding an event shows each assigned athlete with their existing results")
	void listsEachAthletesExistingResults() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T63 saving a valid result stores it beside the athlete")
	void savesValidResult() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T64 adding another result for the same athlete creates a new record, first unchanged")
	void addsSecondResultForSameAthlete() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T65 editing a result updates the same record, no new row")
	void editingResultUpdatesSameRecord() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T66 Edit All Results saves every change in one save, unchanged rows untouched")
	void editAllResultsSavesAllChangesAtOnce() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T67 event note for an athlete is saved with a timestamp and linked to the meet and event")
	void savesEventNoteWithTimestamp() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T68 athlete profile lists every meet, event and performance, notes timestamped")
	void profileShowsMeetAndEventHistory() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T72 blank result returns 400 VALIDATION_ERROR naming result, nothing saved")
	void blankResultIsRejected() throws Exception {
		long event = createEvent();
		long athlete = createAthlete("Jane", "Doe");

		mockMvc.perform(post("/api/events/{id}/results", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(athlete, "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("result:")));

		assertEquals(0, eventResultRepository.count());
		assertEquals(0, participationRepository.count());
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T73 result in the wrong format is rejected, nothing saved (per R1)")
	void wronglyFormattedResultIsRejected() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Pending team decision: Dev's API returns 201 and auto-assigns an unassigned athlete")
	@DisplayName("T74 result for an athlete not assigned to the event is rejected, nothing saved")
	void resultForUnassignedAthleteIsRejected() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T77 Edit All Results with one invalid value saves nothing until fixed (per R3)")
	void editAllWithOneInvalidValue() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T78 removing a result removes only that result")
	void removingResultKeepsAthletesOtherResults() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T80 empty or whitespace-only note is rejected, nothing saved")
	void blankNoteIsRejected() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T82 unknown result, event or athlete ID returns 404; nothing changed")
	void unknownResultOrEventReturnsNotFound() throws Exception {
		long event = createEvent();
		long athlete = createAthlete("Jane", "Doe");
		long stored = postForId("/api/events/" + event + "/results", resultJson(athlete, "12.40"), "$.resultId");
		long unknownResult = unknownId("event_results");

		mockMvc.perform(put("/api/events/{id}/results/{resultId}", event, unknownResult)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(athlete, "11.90")))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/events/{id}/results/{resultId}", event, unknownResult))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/events/{id}/results", unknownId("events")))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/events/{id}/results", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(unknownId("athletes"), "11.90")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));

		assertEquals(1, eventResultRepository.count());
		assertEquals(1, participationRepository.count());
		assertEquals(1, count("SELECT COUNT(*) FROM event_results WHERE id = ? AND event_id = ? AND result = ?",
				stored, event, "12.40"));
	}
}
